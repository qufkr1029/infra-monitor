package com.inframonitor.metrics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

@Service
public class MetricsService {
    private final MetricsRepository repository;
    private final MetricsWriteService writer;

    public MetricsService(MetricsRepository repository, MetricsWriteService writer) {
        this.repository = repository;
        this.writer = writer;
    }

    public void accept(MetricsRequest metrics) {
        try {
            writer.insert(metrics);
        } catch (DataIntegrityViolationException error) {
            // Check after the write transaction rolls back, including concurrent duplicates.
            if (!repository.existsByHostIdAndCollectedAt(metrics.hostId(),
                    ServerMetric.storageTimestamp(metrics.collectedAt()))) {
                throw error;
            }
        }
    }

    @Transactional(readOnly = true)
    public List<MetricsSnapshot> findAll() {
        return repository.findLatestForAllHosts().stream().map(ServerMetric::toSnapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<MetricsSnapshot> findByHostId(String hostId) {
        return repository.findLatestForHost(hostId).map(ServerMetric::toSnapshot);
    }

    public void checkConnection() {
        repository.checkConnection();
    }
}
