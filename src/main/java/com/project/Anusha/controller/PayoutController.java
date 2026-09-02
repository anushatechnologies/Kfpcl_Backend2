package com.project.Anusha.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.Anusha.model.Payout;
import com.project.Anusha.service.PayoutService;

@RestController
@RequestMapping("/api/payouts")
@CrossOrigin(origins = "*")
public class PayoutController {

    @Autowired
    private PayoutService payoutService;

    /**
     * Generate weekly payouts for all delivery persons
     */
    @PostMapping("/generate-weekly")
    public ResponseEntity<?> generateWeeklyPayouts() {
        try {
            List<Payout> payouts = payoutService.generateWeeklyPayouts();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Weekly payouts generated successfully");
            response.put("payouts", payouts);
            response.put("count", payouts.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to generate weekly payouts: " + e.getMessage()));
        }
    }

    /**
     * Get payouts for delivery person
     */
    @GetMapping("/delivery-person/{deliveryPersonId}")
    public ResponseEntity<?> getPayoutsForDeliveryPerson(@PathVariable Long deliveryPersonId) {
        try {
            List<Payout> payouts = payoutService.getPayoutsForDeliveryPerson(deliveryPersonId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payouts", payouts);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch payouts: " + e.getMessage()));
        }
    }

    /**
     * Get recent payouts for delivery person
     */
    @GetMapping("/delivery-person/{deliveryPersonId}/recent")
    public ResponseEntity<?> getRecentPayoutsForDeliveryPerson(
            @PathVariable Long deliveryPersonId,
            @RequestParam(defaultValue = "5") int limit) {
        try {
            List<Payout> payouts = payoutService.getRecentPayoutsForDeliveryPerson(deliveryPersonId, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("recentPayouts", payouts);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch recent payouts: " + e.getMessage()));
        }
    }

    /**
     * Get pending payouts for processing
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingPayouts() {
        try {
            List<Payout> payouts = payoutService.getPendingPayouts();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("pendingPayouts", payouts);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch pending payouts: " + e.getMessage()));
        }
    }

    /**
     * Get all payouts
     */
    @GetMapping
    public ResponseEntity<?> getAllPayouts() {
        try {
            List<Payout> payouts = payoutService.getAllPayouts();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payouts", payouts);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch payouts: " + e.getMessage()));
        }
    }

    /**
     * Get payout by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPayoutById(@PathVariable Long id) {
        try {
            Payout payout = payoutService.getPayoutById(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payout", payout);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch payout: " + e.getMessage()));
        }
    }

    /**
     * Process payout
     */
    @PostMapping("/{id}/process")
    public ResponseEntity<?> processPayout(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            String transactionId = (String) request.get("transactionId");
            String paymentMethod = (String) request.get("paymentMethod");
            Long adminId = request.containsKey("adminId") ? ((Number) request.get("adminId")).longValue() : null;

            if (transactionId == null || paymentMethod == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Transaction ID and payment method are required"));
            }

            Payout payout = payoutService.processPayout(id, transactionId, paymentMethod, adminId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payout processed successfully");
            response.put("payout", payout);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to process payout: " + e.getMessage()));
        }
    }

    /**
     * Fail payout
     */
    @PostMapping("/{id}/fail")
    public ResponseEntity<?> failPayout(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String remarks = request.get("remarks");
            Long adminId = request.containsKey("adminId") ? Long.valueOf(request.get("adminId")) : null;

            if (remarks == null || remarks.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Remarks are required for failing payout"));
            }

            Payout payout = payoutService.failPayout(id, remarks, adminId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payout marked as failed");
            response.put("payout", payout);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fail payout: " + e.getMessage()));
        }
    }

    /**
     * Cancel payout
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelPayout(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String remarks = request.get("remarks");
            Long adminId = request.containsKey("adminId") ? Long.valueOf(request.get("adminId")) : null;

            if (remarks == null || remarks.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Remarks are required for cancelling payout"));
            }

            Payout payout = payoutService.cancelPayout(id, remarks, adminId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payout cancelled successfully");
            response.put("payout", payout);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to cancel payout: " + e.getMessage()));
        }
    }

    /**
     * Retry failed payout
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryFailedPayout(@PathVariable Long id) {
        try {
            Payout payout = payoutService.retryFailedPayout(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Failed payout moved to pending for retry");
            response.put("payout", payout);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retry payout: " + e.getMessage()));
        }
    }

    /**
     * Get payout statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getPayoutStatistics() {
        try {
            Map<String, Object> statistics = payoutService.getPayoutStatistics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", statistics);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch payout statistics: " + e.getMessage()));
        }
    }

    /**
     * Get total paid amount for delivery person
     */
    @GetMapping("/delivery-person/{deliveryPersonId}/total-paid")
    public ResponseEntity<?> getTotalPaidAmountForDeliveryPerson(@PathVariable Long deliveryPersonId) {
        try {
            Optional<Double> totalPaidAmount = payoutService.getTotalPaidAmountForDeliveryPerson(deliveryPersonId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deliveryPersonId", deliveryPersonId);
            response.put("totalPaidAmount", totalPaidAmount.orElse(0.0));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch total paid amount: " + e.getMessage()));
        }
    }
}
