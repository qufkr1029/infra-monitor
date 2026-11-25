package com.inframonitor.metrics;

import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MetricsController {
    private final MetricsService service;

    public MetricsController(MetricsService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        try {
            service.checkConnection();
        } catch (DataAccessException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable");
        }
        return Map.of("status", "UP");
    }

    @PostMapping("/metrics")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@Valid @RequestBody MetricsRequest metrics) {
        service.accept(metrics);
    }

    @GetMapping("/metrics/latest")
    public List<MetricsSnapshot> latest() {
        return service.findAll();
    }

    @GetMapping("/metrics/latest/{hostId}")
    public MetricsSnapshot latestForHost(@PathVariable String hostId) {
        return service.findByHostId(hostId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No metrics for host"));
    }
}
