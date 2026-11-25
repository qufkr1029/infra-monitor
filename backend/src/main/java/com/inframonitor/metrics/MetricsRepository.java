package com.inframonitor.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MetricsRepository extends JpaRepository<ServerMetric, Long> {
    boolean existsByHostIdAndCollectedAt(String hostId, Instant collectedAt);

    @Query("""
            select distinct metric from ServerMetric metric
            left join fetch metric.filesystems
            where not exists (
                select newer.id from ServerMetric newer
                where newer.hostId = metric.hostId and newer.collectedAt > metric.collectedAt
            )
            order by metric.hostId
            """)
    List<ServerMetric> findLatestForAllHosts();

    @Query("""
            select distinct metric from ServerMetric metric
            left join fetch metric.filesystems
            where metric.hostId = :hostId
              and metric.collectedAt = (
                  select max(sample.collectedAt) from ServerMetric sample where sample.hostId = :hostId
              )
            """)
    Optional<ServerMetric> findLatestForHost(@Param("hostId") String hostId);

    @Query(value = "SELECT 1", nativeQuery = true)
    int checkConnection();
}
