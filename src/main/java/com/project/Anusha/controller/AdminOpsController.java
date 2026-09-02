package com.project.Anusha.controller;

import com.project.Anusha.dto.AdminBulkProductActionRequest;
import com.project.Anusha.service.AdminOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminOpsController {

    private final AdminOpsService adminOpsService;

    @GetMapping("/command-center")
    public ResponseEntity<Map<String, Object>> getCommandCenter(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "50") int lowStockThreshold,
            @RequestParam(defaultValue = "2") int delayedHours
    ) {
        return ResponseEntity.ok(adminOpsService.getCommandCenter(period, lowStockThreshold, delayedHours));
    }

    @PostMapping("/products/bulk-action")
    public ResponseEntity<Map<String, Object>> applyBulkProductAction(
            @RequestBody AdminBulkProductActionRequest request,
            Authentication authentication
    ) {
        String actorEmail = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(adminOpsService.applyBulkProductAction(request, actorEmail));
    }

    /**
     * Alias endpoint for bulk actions (UI-friendly).
     * POST /api/admin/products/bulk
     */
    @PostMapping("/products/bulk")
    public ResponseEntity<Map<String, Object>> applyBulkProductActionAlias(
            @RequestBody AdminBulkProductActionRequest request,
            Authentication authentication
    ) {
        String actorEmail = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(adminOpsService.applyBulkProductAction(request, actorEmail));
    }
}
