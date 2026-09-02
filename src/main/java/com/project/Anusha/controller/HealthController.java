package com.project.Anusha.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight health check endpoint used by load balancers (AWS ALB, Nginx,
 * Kubernetes probes) to verify this instance is alive and ready.
 *
 * GET /api/health  → 200 { status: "UP", timestamp: "..." }
 */
@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "AnushaBazaar API",
                "timestamp", Instant.now().toString()
        ));
    }

    /** Kubernetes / ECS readiness probe */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        return ResponseEntity.ok(Map.of("status", "READY"));
    }

    /** Kubernetes liveness probe */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        return ResponseEntity.ok(Map.of("status", "ALIVE"));
    }
}
