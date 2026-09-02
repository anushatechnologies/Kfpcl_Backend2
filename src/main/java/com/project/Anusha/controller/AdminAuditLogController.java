package com.project.Anusha.controller;

import com.project.Anusha.model.UserLog;
import com.project.Anusha.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    /**
     * Track every admin action (audit log).
     *
     * GET /api/admin/audit-logs?role=ADMIN&q=bulk&page=0&size=50
     * GET /api/admin/audit-logs?from=2026-04-22T00:00:00&to=2026-04-22T23:59:59
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(value = "role", required = false, defaultValue = "ADMIN") String role,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size
    ) {
        int cappedSize = Math.min(Math.max(size, 1), 200);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), cappedSize, Sort.by("timestamp").descending());

        Page<UserLog> result = adminAuditLogService.search(role, q, from, to, pageable);

        Map<String, Object> payload = new HashMap<>();
        payload.put("logs", result.getContent());
        payload.put("totalElements", result.getTotalElements());
        payload.put("totalPages", result.getTotalPages());
        payload.put("currentPage", result.getNumber());
        return ResponseEntity.ok(payload);
    }
}

