CREATE TABLE IF NOT EXISTS server_metrics (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    host_id VARCHAR(255) NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cpu_used_percent DOUBLE PRECISION NOT NULL CHECK (cpu_used_percent BETWEEN 0 AND 100),
    memory_total_bytes BIGINT NOT NULL CHECK (memory_total_bytes > 0),
    memory_used_bytes BIGINT NOT NULL CHECK (memory_used_bytes >= 0),
    memory_available_bytes BIGINT NOT NULL CHECK (memory_available_bytes >= 0),
    memory_used_percent DOUBLE PRECISION NOT NULL CHECK (memory_used_percent BETWEEN 0 AND 100),
    CHECK (memory_used_bytes <= memory_total_bytes),
    CHECK (memory_available_bytes <= memory_total_bytes - memory_used_bytes)
);

-- Also supports looking up each host's newest sample without a duplicate index.
CREATE UNIQUE INDEX IF NOT EXISTS ux_server_metrics_host_collected
    ON server_metrics (host_id, collected_at DESC);

CREATE TABLE IF NOT EXISTS filesystem_metrics (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    metric_id BIGINT NOT NULL REFERENCES server_metrics(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position >= 0),
    mountpoint TEXT NOT NULL,
    total_bytes BIGINT NOT NULL CHECK (total_bytes > 0),
    used_bytes BIGINT NOT NULL CHECK (used_bytes >= 0),
    available_bytes BIGINT NOT NULL CHECK (available_bytes >= 0),
    used_percent DOUBLE PRECISION NOT NULL CHECK (used_percent BETWEEN 0 AND 100),
    CONSTRAINT uk_filesystem_metrics_position UNIQUE (metric_id, position),
    CHECK (used_bytes <= total_bytes),
    CHECK (available_bytes <= total_bytes - used_bytes)
);
