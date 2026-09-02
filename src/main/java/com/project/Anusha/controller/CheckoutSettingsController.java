package com.project.Anusha.controller;

import com.project.Anusha.dto.CheckoutSettingsResponse;
import com.project.Anusha.dto.CheckoutSettingsUpdateRequest;
import com.project.Anusha.service.CheckoutSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CheckoutSettingsController {

    private final CheckoutSettingsService checkoutSettingsService;

    @GetMapping("/api/checkout-settings")
    public ResponseEntity<?> getCheckoutSettings() {
        try {
            CheckoutSettingsResponse settings = checkoutSettingsService.getCurrentSettingsResponse();
            return ResponseEntity.ok(Map.of("success", true, "settings", settings));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Failed to fetch checkout settings: " + e.getMessage()));
        }
    }

    @GetMapping("/api/admin/checkout-settings")
    public ResponseEntity<?> getAdminCheckoutSettings() {
        return getCheckoutSettings();
    }

    @PutMapping("/api/admin/checkout-settings")
    public ResponseEntity<?> updateCheckoutSettings(@RequestBody CheckoutSettingsUpdateRequest request) {
        try {
            CheckoutSettingsResponse settings = checkoutSettingsService.updateSettings(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Checkout settings updated successfully",
                    "settings", settings
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Failed to update checkout settings: " + e.getMessage()));
        }
    }
}
