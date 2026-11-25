"""Print Linux host metrics as one JSON object per sampling interval."""

import json
import math
import os
from pathlib import Path
import signal
import sys
import threading
from datetime import datetime, timezone


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
    stop = threading.Event()
    for signum in (signal.SIGINT, signal.SIGTERM):
        signal.signal(signum, lambda *_: stop.set())

    try:
        interval = float(os.environ.get("COLLECTION_INTERVAL_SECONDS", "5"))
        if not math.isfinite(interval) or interval <= 0:
            raise ValueError("COLLECTION_INTERVAL_SECONDS must be finite and positive")
        proc_path = Path(os.environ.get("HOST_PROC_PATH", "/host/proc"))
        root_path = Path(os.environ.get("HOST_ROOT_PATH", "/host/root"))
        host_id = (root_path / "etc/hostname").read_text(encoding="utf-8").strip()
        previous = read_cpu(proc_path)
        if not hasattr(os, "statvfs"):
            raise RuntimeError("Linux is required; run Docker Compose on the target Linux host")
        read_memory(proc_path)
        read_filesystem(root_path)
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        print(f"Collector startup failed: {error}", file=sys.stderr, flush=True)
        return 1

    while not stop.wait(interval):
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
            print(json.dumps(sample), flush=True)
        except (OSError, ValueError, KeyError) as error:
            print(f"Collection failed: {error}", file=sys.stderr, flush=True)
            # Reset the baseline so the next success measures a fresh interval.
            try:
                previous = read_cpu(proc_path)
            except (OSError, ValueError):
                pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
