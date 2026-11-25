package com.inframonitor.metrics;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "server_metrics", uniqueConstraints =
        @UniqueConstraint(name = "ux_server_metrics_host_collected", columnNames = {"host_id", "collected_at"}))
public class ServerMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_id", nullable = false, length = 255)
    private String hostId;
    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "cpu_used_percent", nullable = false)
    private double cpuUsedPercent;
    @Column(name = "memory_total_bytes", nullable = false)
    private long memoryTotalBytes;
    @Column(name = "memory_used_bytes", nullable = false)
    private long memoryUsedBytes;
    @Column(name = "memory_available_bytes", nullable = false)
    private long memoryAvailableBytes;
    @Column(name = "memory_used_percent", nullable = false)
    private double memoryUsedPercent;

    @OneToMany(mappedBy = "metric", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<FilesystemMetric> filesystems = new ArrayList<>();

    protected ServerMetric() {}

    public ServerMetric(MetricsRequest request) {
        hostId = request.hostId();
        collectedAt = storageTimestamp(request.collectedAt());
        receivedAt = storageTimestamp(Instant.now());
        cpuUsedPercent = request.cpu().usedPercent();
        memoryTotalBytes = request.memory().totalBytes();
        memoryUsedBytes = request.memory().usedBytes();
        memoryAvailableBytes = request.memory().availableBytes();
        memoryUsedPercent = request.memory().usedPercent();
        for (int position = 0; position < request.filesystems().size(); position++) {
            filesystems.add(new FilesystemMetric(this, position, request.filesystems().get(position)));
        }
    }

    static Instant storageTimestamp(Instant timestamp) {
        return timestamp.truncatedTo(ChronoUnit.MICROS);
    }

    MetricsSnapshot toSnapshot() {
        return new MetricsSnapshot(new MetricsRequest(hostId, collectedAt,
                new MetricsRequest.Cpu(cpuUsedPercent),
                new MetricsRequest.Memory(memoryTotalBytes, memoryAvailableBytes, memoryUsedBytes, memoryUsedPercent),
                filesystems.stream().map(FilesystemMetric::toMetric).toList()), receivedAt);
    }
}
