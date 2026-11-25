package com.inframonitor.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "filesystem_metrics", uniqueConstraints =
        @UniqueConstraint(name = "uk_filesystem_metrics_position", columnNames = {"metric_id", "position"}))
public class FilesystemMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metric_id", nullable = false)
    private ServerMetric metric;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false, columnDefinition = "text")
    private String mountpoint;
    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;
    @Column(name = "used_bytes", nullable = false)
    private long usedBytes;
    @Column(name = "available_bytes", nullable = false)
    private long availableBytes;
    @Column(name = "used_percent", nullable = false)
    private double usedPercent;

    protected FilesystemMetric() {}

    FilesystemMetric(ServerMetric metric, int position, MetricsRequest.Filesystem filesystem) {
        this.metric = metric;
        this.position = position;
        mountpoint = filesystem.mountpoint();
        totalBytes = filesystem.totalBytes();
        usedBytes = filesystem.usedBytes();
        availableBytes = filesystem.availableBytes();
        usedPercent = filesystem.usedPercent();
    }

    MetricsRequest.Filesystem toMetric() {
        return new MetricsRequest.Filesystem(mountpoint, totalBytes, usedBytes, availableBytes, usedPercent);
    }
}
