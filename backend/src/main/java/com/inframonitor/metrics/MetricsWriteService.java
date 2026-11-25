package com.inframonitor.metrics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricsWriteService {
    private final MetricsRepository repository;

    public MetricsWriteService(MetricsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void insert(MetricsRequest metrics) {
        var timestamp = ServerMetric.storageTimestamp(metrics.collectedAt());
        if (!repository.existsByHostIdAndCollectedAt(metrics.hostId(), timestamp)) {
            repository.saveAndFlush(new ServerMetric(metrics));
        }
    }
}
