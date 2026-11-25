"use strict";

(() => {
  const POLL_INTERVAL = 10_000;
  const STALE_AFTER = 30_000;
  const $ = (id) => document.getElementById(id);
  const dateFormat = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
  });
  let snapshots = [];
  let selectedHost = "";
  let lastError = "";
  let loading = false;

  function timeLabel(value) {
    const date = new Date(value);
    return Number.isFinite(date.getTime()) ? dateFormat.format(date) : "—";
  }

  function percent(value) {
    return typeof value === "number" && Number.isFinite(value) ? value.toFixed(1) : "—";
  }

  function capacity(value) {
    return typeof value === "number" && Number.isFinite(value)
      ? `${(value / 1024 ** 3).toLocaleString("ko-KR", { maximumFractionDigits: 2 })} GiB` : "—";
  }

  function setProgress(element, value) {
    const valid = typeof value === "number" && Number.isFinite(value);
    element.value = valid ? Math.max(0, Math.min(100, value)) : 0;
    element.setAttribute("aria-valuetext", valid ? `${percent(value)}%` : "데이터 없음");
  }

  function showNotice(message, kind = "") {
    if ($("notice").textContent !== message) $("notice").textContent = message;
    $("notice").dataset.kind = kind;
    $("notice").hidden = !message;
  }

  function setStatus(label, state = "") {
    if ($("connection").textContent !== label) $("connection").textContent = label;
    $("connection").dataset.state = state;
  }

  function currentSnapshot() {
    return snapshots.find((snapshot) => snapshot.metrics.host_id === selectedHost);
  }

  function updateFreshness() {
    const snapshot = currentSnapshot();
    const collected = snapshot ? Date.parse(snapshot.metrics.collected_at) : NaN;
    const age = Number.isFinite(collected) ? Math.max(0, Date.now() - collected) : NaN;
    $("sample-age").textContent = Number.isFinite(age)
      ? `${Math.floor(age / 1000).toLocaleString("ko-KR")}초 전 수집` : "수집 대기";
    if (lastError) {
      setStatus("조회 실패", "error");
      showNotice(lastError + (snapshot ? " 마지막으로 조회한 값을 표시합니다." : ""), "error");
    } else if (!snapshot) {
      setStatus("수집 대기");
      showNotice("아직 수집된 데이터가 없습니다. 수집기가 데이터를 보내면 자동으로 표시됩니다.");
    } else if (!Number.isFinite(age) || age > STALE_AFTER) {
      setStatus("수집 지연", "stale");
      showNotice("30초 이상 새 지표가 수집되지 않았습니다. 마지막 수집값을 표시합니다.", "stale");
    } else {
      setStatus("정상 수집", "ok");
      showNotice("");
    }
  }

  function filesystemRow(filesystem) {
    const row = document.createElement("div");
    row.className = "filesystem-row";
    const path = document.createElement("span");
    path.className = "filesystem-path";
    path.textContent = filesystem.mountpoint;
    const detail = document.createElement("div");
    const space = document.createElement("div");
    space.className = "filesystem-space";
    space.textContent = `사용 ${capacity(filesystem.used_bytes)} / 전체 ${capacity(filesystem.total_bytes)} · 가용 ${capacity(filesystem.available_bytes)}`;
    const bar = document.createElement("progress");
    bar.max = 100;
    bar.setAttribute("aria-label", `${filesystem.mountpoint} 사용률`);
    setProgress(bar, filesystem.used_percent);
    detail.append(space, bar);
    const usage = document.createElement("span");
    usage.className = "filesystem-percent";
    usage.textContent = `${percent(filesystem.used_percent)}%`;
    row.append(path, detail, usage);
    return row;
  }

  function render() {
    const snapshot = currentSnapshot();
    const metrics = snapshot?.metrics;
    const filesystem = metrics?.filesystems.find((item) => item.mountpoint === "/") || metrics?.filesystems[0];
    $("cpu-value").textContent = percent(metrics?.cpu.used_percent);
    $("memory-value").textContent = percent(metrics?.memory.used_percent);
    $("filesystem-value").textContent = percent(filesystem?.used_percent);
    setProgress($("cpu-bar"), metrics?.cpu.used_percent);
    setProgress($("memory-bar"), metrics?.memory.used_percent);
    setProgress($("filesystem-bar"), filesystem?.used_percent);
    $("memory-capacity").textContent = `사용 ${capacity(metrics?.memory.used_bytes)} / 전체 ${capacity(metrics?.memory.total_bytes)}`;
    $("filesystem-capacity").textContent = `사용 ${capacity(filesystem?.used_bytes)} / 전체 ${capacity(filesystem?.total_bytes)}`;
    $("filesystem-mount").textContent = filesystem?.mountpoint || "/";
    $("collected-at").textContent = snapshot ? timeLabel(metrics.collected_at) : "—";
    $("received-at").textContent = snapshot ? timeLabel(snapshot.received_at) : "—";
    const list = $("filesystem-list");
    list.replaceChildren();
    if (metrics?.filesystems.length) {
      metrics.filesystems.forEach((item) => list.append(filesystemRow(item)));
    } else {
      const empty = document.createElement("p");
      empty.className = "empty";
      empty.textContent = "수집된 파일시스템이 없습니다.";
      list.append(empty);
    }
    updateFreshness();
  }

  async function refresh() {
    if (loading) return;
    loading = true;
    $("refresh").disabled = true;
    $("refresh").textContent = "조회 중";
    $("metrics").setAttribute("aria-busy", "true");
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5_000);
    try {
      const response = await fetch("/api/metrics/latest", { cache: "no-store", signal: controller.signal });
      if (!response.ok) throw new Error(`지표 조회에 실패했습니다. (HTTP ${response.status})`);
      const result = await response.json();
      if (!Array.isArray(result) || !result.every((item) => item?.metrics
          && typeof item.metrics.host_id === "string" && item.metrics.cpu && item.metrics.memory
          && Array.isArray(item.metrics.filesystems) && item.metrics.filesystems.every((filesystem) => filesystem))) {
        throw new Error("지표 응답 형식을 확인할 수 없습니다.");
      }
      snapshots = result;
      if (!currentSnapshot()) selectedHost = snapshots[0]?.metrics.host_id || "";
      const select = $("server");
      select.replaceChildren();
      for (const snapshot of snapshots) {
        const option = document.createElement("option");
        option.value = snapshot.metrics.host_id;
        option.textContent = snapshot.metrics.host_id;
        select.append(option);
      }
      if (!snapshots.length) {
        const option = document.createElement("option");
        option.value = "";
        option.textContent = "등록된 서버 없음";
        select.append(option);
      }
      select.disabled = snapshots.length === 0;
      select.value = selectedHost;
      $("server-count").textContent = `${snapshots.length}대`;
      $("checked-at").textContent = `마지막 조회 ${timeLabel(Date.now())}`;
      lastError = "";
      render();
    } catch (error) {
      lastError = error.name === "AbortError" ? "백엔드 응답이 지연되고 있습니다."
        : error instanceof TypeError ? "백엔드에 연결할 수 없습니다." : error.message;
      if (!snapshots.length && $("server").options.length) {
        $("server").options[0].textContent = "서버 조회 실패";
      }
      render();
    } finally {
      clearTimeout(timeout);
      loading = false;
      $("refresh").disabled = false;
      $("refresh").textContent = "새로고침";
      $("metrics").setAttribute("aria-busy", "false");
    }
  }

  $("server").addEventListener("change", () => { selectedHost = $("server").value; render(); });
  $("refresh").addEventListener("click", refresh);
  setInterval(refresh, POLL_INTERVAL);
  setInterval(() => { if (!loading) updateFreshness(); }, 1_000);
  refresh();
})();
