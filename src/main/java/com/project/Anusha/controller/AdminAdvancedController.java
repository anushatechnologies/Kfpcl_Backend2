package com.project.Anusha.controller;

import com.project.Anusha.model.ApprovalRequest;
import com.project.Anusha.model.CampaignDraft;
import com.project.Anusha.model.DeliveryZone;
import com.project.Anusha.model.Product;
import com.project.Anusha.service.AdminAdvancedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminAdvancedController {

    private final AdminAdvancedService adminAdvancedService;

    @PutMapping("/products/{id}/lifecycle")
    public ResponseEntity<Product> updateProductLifecycle(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(adminAdvancedService.updateProductLifecycle(id, payload, actor));
    }

    @GetMapping("/approvals")
    public ResponseEntity<List<ApprovalRequest>> listApprovals() {
        return ResponseEntity.ok(adminAdvancedService.listApprovalRequests());
    }

    @PostMapping("/approvals")
    public ResponseEntity<ApprovalRequest> createApproval(
            @RequestBody Map<String, Object> payload,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(adminAdvancedService.createApprovalRequest(payload, actor));
    }

    @PutMapping("/approvals/{id}")
    public ResponseEntity<ApprovalRequest> decideApproval(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(adminAdvancedService.decideApproval(id, payload, actor));
    }

    @GetMapping("/delivery-zones")
    public ResponseEntity<List<DeliveryZone>> listZones() {
        return ResponseEntity.ok(adminAdvancedService.listZones());
    }

    @PostMapping("/delivery-zones")
    public ResponseEntity<DeliveryZone> createZone(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(adminAdvancedService.saveZone(payload));
    }

    @PutMapping("/delivery-zones/{id}")
    public ResponseEntity<DeliveryZone> updateZone(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(adminAdvancedService.updateZone(id, payload));
    }

    @GetMapping("/campaign-targeting")
    public ResponseEntity<Map<String, Object>> getCampaignTargeting() {
        return ResponseEntity.ok(adminAdvancedService.getCampaignTargeting());
    }

    @GetMapping("/campaign-drafts")
    public ResponseEntity<List<CampaignDraft>> listCampaignDrafts() {
        return ResponseEntity.ok(adminAdvancedService.listCampaignDrafts());
    }

    @PostMapping("/campaign-drafts")
    public ResponseEntity<CampaignDraft> createCampaignDraft(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(adminAdvancedService.saveCampaignDraft(payload));
    }

    @GetMapping("/fraud-scoring")
    public ResponseEntity<Map<String, Object>> getFraudScoring() {
        return ResponseEntity.ok(adminAdvancedService.getFraudScores());
    }

    @GetMapping("/smart-insights")
    public ResponseEntity<Map<String, Object>> getSmartInsights(@RequestParam(defaultValue = "month") String period) {
        return ResponseEntity.ok(adminAdvancedService.getSmartInsights(period));
    }

    @PostMapping("/smart-product-content")
    public ResponseEntity<Map<String, Object>> generateSmartProductContent(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(adminAdvancedService.generateSmartProductContent(payload));
    }

    @GetMapping("/exports/{type}")
    public ResponseEntity<byte[]> exportData(
            @PathVariable String type,
            @RequestParam(defaultValue = "csv") String format
    ) {
        AdminAdvancedService.ExportPayload payload = adminAdvancedService.exportData(type, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, payload.contentType())
                .body(payload.data());
    }
}
