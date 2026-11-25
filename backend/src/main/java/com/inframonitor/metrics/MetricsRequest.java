package com.inframonitor.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record MetricsRequest(
        @JsonProperty("host_id") @NotBlank @Size(max = 255) String hostId,
        @JsonProperty("collected_at") @NotNull Instant collectedAt,
        @NotNull @Valid Cpu cpu,
        @NotNull @Valid Memory memory,
        @NotEmpty List<@NotNull @Valid Filesystem> filesystems
) {
    public MetricsRequest {
        if (filesystems != null) {
            // Validation must be allowed to report null elements as HTTP 400.
            filesystems = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(filesystems));
        }
    }

    public record Cpu(
            @JsonProperty("used_percent") @NotNull @DecimalMin("0") @DecimalMax("100") Double usedPercent
    ) {}

    public record Memory(
            @JsonProperty("total_bytes") @NotNull @Positive Long totalBytes,
            @JsonProperty("available_bytes") @NotNull @PositiveOrZero Long availableBytes,
            @JsonProperty("used_bytes") @NotNull @PositiveOrZero Long usedBytes,
            @JsonProperty("used_percent") @NotNull @DecimalMin("0") @DecimalMax("100") Double usedPercent
    ) {
        @AssertTrue(message = "used and available memory must fit within total memory")
        @JsonIgnore
        public boolean isCapacityValid() {
            return capacityValid(totalBytes, usedBytes, availableBytes);
        }
    }

    public record Filesystem(
            @NotBlank String mountpoint,
            @JsonProperty("total_bytes") @NotNull @Positive Long totalBytes,
            @JsonProperty("used_bytes") @NotNull @PositiveOrZero Long usedBytes,
            @JsonProperty("available_bytes") @NotNull @PositiveOrZero Long availableBytes,
            @JsonProperty("used_percent") @NotNull @DecimalMin("0") @DecimalMax("100") Double usedPercent
    ) {
        @AssertTrue(message = "used and available space must fit within total space")
        @JsonIgnore
        public boolean isCapacityValid() {
            return capacityValid(totalBytes, usedBytes, availableBytes);
        }
    }

    private static boolean capacityValid(Long total, Long used, Long available) {
        // Field constraints handle missing or negative values independently.
        return total == null || used == null || available == null
                || (used <= total && available <= total - used);
    }
}
