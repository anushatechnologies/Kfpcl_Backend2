package com.project.Anusha.controller;

import com.project.Anusha.service.AdminDashboardLiveService;
import com.project.Anusha.service.AdminDashboardStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminDashboardLiveController {

    private final AdminDashboardLiveService adminDashboardLiveService;
    private final AdminDashboardStreamService adminDashboardStreamService;

    /**
     * Real-time business dashboard snapshot.
     * GET /api/admin/dashboard/live?activeWindowMinutes=15
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> live(
            @RequestParam(value = "activeWindowMinutes", defaultValue = "15") int activeWindowMinutes
    ) {
        return ResponseEntity.ok(adminDashboardLiveService.getLiveSnapshot(activeWindowMinutes));
    }

    /**
     * Bar graph dataset for dashboard.
     * GET /api/admin/dashboard/bar?period=7d
     */
    @GetMapping("/bar")
    public ResponseEntity<List<Map<String, Object>>> bar(
            @RequestParam(value = "period", defaultValue = "7d") String period
    ) {
        return ResponseEntity.ok(adminDashboardLiveService.getOrdersRevenueBar(period));
    }

    /**
     * Server-sent events (SSE) stream.
     * GET /api/admin/dashboard/stream
     */
    @GetMapping("/stream")
    public SseEmitter stream(
            @RequestParam(value = "activeWindowMinutes", defaultValue = "15") int activeWindowMinutes
    ) {
        return adminDashboardStreamService.connect(activeWindowMinutes);
    }
}

