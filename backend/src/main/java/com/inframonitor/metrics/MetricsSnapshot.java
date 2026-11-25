package com.inframonitor.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record MetricsSnapshot(
        MetricsRequest metrics,
        @JsonProperty("received_at") Instant receivedAt
) {}
