package com.project.Anusha.controller;

import com.project.Anusha.model.AppPolicy;
import com.project.Anusha.service.AppPolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class AppPolicyController {

    @Autowired
    private AppPolicyService policyService;

    /**
     * Get policy by type (Public)
     */
    @GetMapping("/api/policies/{type}")
    public ResponseEntity<?> getPolicy(@PathVariable String type) {
        return policyService.getPolicyByType(type)
                .map(policy -> ResponseEntity.ok(Map.of("success", true, "policy", policy)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update policy (Admin)
     */
    @PutMapping("/api/admin/policies/{type}")
    public ResponseEntity<?> updatePolicy(@PathVariable String type, @RequestBody Map<String, String> request) {
        String content = request.get("content");
        if (content == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content is required"));
        }
        
        AppPolicy updatedPolicy = policyService.updatePolicy(type, content);
        
        return ResponseEntity.ok(Map.of(
            "success", true, 
            "message", "Policy updated successfully",
            "policy", updatedPolicy
        ));
    }
}
