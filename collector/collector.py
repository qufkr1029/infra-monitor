"""Print Linux host metrics as one JSON object per sampling interval."""

import json
import math
import os
from pathlib import Path
import signal
import sys
import threading
import time
from datetime import datetime, timedelta, timezone
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import ProxyHandler, Request, build_opener


KST = timezone(timedelta(hours=9), "KST")
# Container-to-container traffic must not be routed through an inherited proxy.
HTTP = build_opener(ProxyHandler({}))


def send_metrics(api_url, line):
    request = Request(
        api_url,
        data=line.encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with HTTP.open(request, timeout=3) as response:
        if not 200 <= response.status < 300:
            raise OSError(f"Metrics API returned HTTP {response.status}")


class CollectorLog:
    """Append daily, numbered files without deleting old logs."""

    def __init__(self, directory, max_bytes):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.max_bytes = max_bytes
        self.date = None
        self.index = 1

    def write(self, line):
        date = datetime.now(KST).date().isoformat()
        if date != self.date:
            indices = [
                int(path.stem.rsplit("-", 1)[1])
                for path in self.directory.glob(f"{date}-*.log")
                if path.stem.rsplit("-", 1)[1].isdigit()
            ]
            self.date = date
            self.index = max(indices, default=1)
        data = (line + "\n").encode("utf-8")
        while True:
            path = self.directory / f"{date}-{self.index}.log"
            size = path.stat().st_size if path.exists() else 0
            if size and size + len(data) > self.max_bytes:
                self.index += 1
                continue
            # Open per record so manually deleting a file does not leave an open inode.
            with path.open("ab") as stream:
                stream.write(data)
            break


def read_cpu(proc_path):
    with (proc_path / "stat").open(encoding="utf-8") as stream:
        fields = stream.readline().split()
    if not fields or fields[0] != "cpu" or len(fields) < 9:
        raise ValueError("Missing aggregate CPU counters in host /proc/stat")
    # guest and guest_nice are already included in user and nice.
    counters = [int(value) for value in fields[1:9]]
    return sum(counters), counters[3] + counters[4]


def cpu_percent(previous, current):
    total = current[0] - previous[0]
    idle = current[1] - previous[1]
    if total <= 0:
        raise ValueError("Host CPU counters did not advance")
    return round(max(0.0, min(100.0, (total - idle) / total * 100)), 2)


def read_memory(proc_path):
    values = {}
    for line in (proc_path / "meminfo").read_text(encoding="utf-8").splitlines():
        key, value = line.split(":", 1)
        if key in ("MemTotal", "MemAvailable"):
            values[key] = int(value.split()[0]) * 1024
    total = values["MemTotal"]
    available = values["MemAvailable"]
    return {
        "total_bytes": total,
        "available_bytes": available,
        "used_bytes": total - available,
        "used_percent": round((total - available) / total * 100, 2),
    }


def read_filesystem(root_path):
    stats = os.statvfs(root_path)
    total = stats.f_blocks * stats.f_frsize
    used = (stats.f_blocks - stats.f_bfree) * stats.f_frsize
    available = stats.f_bavail * stats.f_frsize
    return {
        "mountpoint": "/",
        "total_bytes": total,
        "used_bytes": used,
        "available_bytes": available,
        # Like df, exclude reserved blocks from the usable-capacity denominator.
        "used_percent": round(used / (used + available) * 100, 2)
        if used + available else 0.0,
    }


def main():
    log = None
    stop = threading.Event()
    for signum in (signal.SIGINT, signal.SIGTERM):
        signal.signal(signum, lambda *_: stop.set())

    try:
        interval = float(os.environ.get("COLLECTION_INTERVAL_SECONDS", "10"))
        if not math.isfinite(interval) or interval <= 0:
            raise ValueError("COLLECTION_INTERVAL_SECONDS must be finite and positive")
        max_bytes = int(os.environ.get("LOG_MAX_BYTES", str(5 * 1024 * 1024)))
        if max_bytes <= 0:
            raise ValueError("LOG_MAX_BYTES must be positive")
        log = CollectorLog(os.environ.get("COLLECTOR_LOG_DIR", "/app/collector_logs"), max_bytes)
        proc_path = Path(os.environ.get("HOST_PROC_PATH", "/host/proc"))
        root_path = Path(os.environ.get("HOST_ROOT_PATH", "/host/root"))
        host_id = (root_path / "etc/hostname").read_text(encoding="utf-8").strip()
        previous = read_cpu(proc_path)
        if not hasattr(os, "statvfs"):
            raise RuntimeError("Linux is required; run Docker Compose on the target Linux host")
        read_memory(proc_path)
        read_filesystem(root_path)
        api_url = os.environ.get("METRICS_API_URL", "").strip()
        if api_url:
            parsed_url = urlsplit(api_url)
            if parsed_url.scheme not in ("http", "https") or not parsed_url.hostname:
                raise ValueError("METRICS_API_URL must be an absolute HTTP(S) URL")
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        report_error(log, f"Collector startup failed: {error}")
        return 1

    next_collection = time.monotonic() + interval
    while not stop.wait(max(0, next_collection - time.monotonic())):
        next_collection = time.monotonic() + interval
        try:
            current = read_cpu(proc_path)
            usage = cpu_percent(previous, current)
            previous = current
            sample = {
                "host_id": host_id,
                "collected_at": datetime.now(timezone.utc).isoformat(),
                "cpu": {"used_percent": usage},
                "memory": read_memory(proc_path),
                "filesystems": [read_filesystem(root_path)],
            }
            line = json.dumps(sample)
            print(line, flush=True)
            try:
                log.write(line)
            except OSError as error:
                report_error(log, f"Log write failed: {error}")
            if api_url:
                try:
                    send_metrics(api_url, line)
                except (OSError, URLError) as error:
                    report_error(log, f"Metrics delivery failed (next interval will retry): {error}")
        except (OSError, ValueError, KeyError) as error:
            report_error(log, f"Collection failed: {error}")
            # Reset the baseline so the next success measures a fresh interval.
            try:
                previous = read_cpu(proc_path)
            except (OSError, ValueError):
                pass
    return 0


def report_error(log, message):
    print(message, file=sys.stderr, flush=True)
    if log is not None:
        try:
            log.write(json.dumps({
                "collected_at": datetime.now(timezone.utc).isoformat(),
                "level": "ERROR",
                "message": message,
            }))
        except OSError as error:
            print(f"Unable to write collector log: {error}", file=sys.stderr, flush=True)


if __name__ == "__main__":
    sys.exit(main())
