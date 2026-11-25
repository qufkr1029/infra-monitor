package com.inframonitor.metrics;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class MetricsService {
    private final ConcurrentMap<String, MetricsSnapshot> latest = new ConcurrentHashMap<>();

    public void accept(MetricsRequest metrics) {
        latest.compute(metrics.hostId(), (hostId, previous) -> {
            if (previous != null && !metrics.collectedAt().isAfter(previous.metrics().collectedAt())) {
                return previous;
            }
            return new MetricsSnapshot(metrics, Instant.now());
        });
    }

    public List<MetricsSnapshot> findAll() {
        return latest.values().stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.metrics().hostId()))
                .toList();
    }

    public Optional<MetricsSnapshot> findByHostId(String hostId) {
        return Optional.ofNullable(latest.get(hostId));
    }
}
