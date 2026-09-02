package com.project.Anusha.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map; 

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.DeliveryPersonDocument;
import com.project.Anusha.model.ProductRating;

import com.project.Anusha.service.AdminService;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /**
     * Get dashboard summary
     */
    @GetMapping("/dashboard/summary")
    public ResponseEntity<?> getDashboardSummary() {
        try {
            Map<String, Object> summary = adminService.getDashboardSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch dashboard summary: " + e.getMessage()));
        }
    }

    /**
     * Get active users count
     */
    @GetMapping("/dashboard/active-users")
    public ResponseEntity<?> getActiveUsers() {
        try {
            Map<String, Long> count = adminService.getActiveUsersCount();
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch active users: " + e.getMessage()));
        }
    }

    /**
     * Get recent orders
     */
    @GetMapping("/dashboard/recent-orders")
    public ResponseEntity<?> getRecentOrders() {
        try {
            List<com.project.Anusha.model.Order> orders = adminService.getRecentOrders();
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch recent orders: " + e.getMessage()));
        }
    }

    /**
     * Get dashboard analytics
     */
    @GetMapping("/dashboard/analytics")
    public ResponseEntity<?> getOrderAnalytics(@RequestParam(defaultValue = "today") String period) {
        try {
            Map<String, Long> analytics = adminService.getOrderAnalytics(period);
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch analytics: " + e.getMessage()));
        }
    }

    @GetMapping("/dashboard/product-performance")
    public ResponseEntity<?> getProductPerformance(@RequestParam(defaultValue = "today") String period) {
        try {
            return ResponseEntity.ok(adminService.getProductPerformance(period));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch product performance: " + e.getMessage()));
        }
    }

    /**
     * Get legacy dashboard statistics
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStatistics() {
        try {
            Map<String, Object> statistics = adminService.getDashboardStatistics();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", statistics);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch dashboard statistics: " + e.getMessage()));
        }
    }

    /**
     * Assign order to delivery person
     */
    @PostMapping("/orders/{orderId}/assign")
    public ResponseEntity<?> assignOrder(@PathVariable Long orderId, @RequestBody Map<String, Object> request) {
        try {
            Object dpIdObj = request.get("deliveryPersonId");
            if (dpIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "deliveryPersonId is required"));
            }
            Long deliveryPersonId = ((Number) dpIdObj).longValue();

            DeliveryOrder assignedOrder = adminService.assignOrderToDeliveryPerson(orderId, deliveryPersonId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Order assigned successfully",
                    "orderId", assignedOrder.getId(),
                    "orderNumber", assignedOrder.getOrderNumber()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to assign order: " + e.getMessage()));
        }
    }

    /**
     * Get all delivery persons pending approval
     */
    @GetMapping("/delivery-persons/pending-approval")
    public ResponseEntity<?> getDeliveryPersonsPendingApproval() {
        try {
            List<DeliveryPerson> deliveryPersons = adminService.getDeliveryPersonsPendingApproval();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deliveryPersons", deliveryPersons);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch delivery persons: " + e.getMessage()));
        }
    }

    /**
     * Get all delivery persons
     */
    @GetMapping("/delivery-persons")
    public ResponseEntity<?> getAllDeliveryPersons() {
        try {
            List<DeliveryPerson> deliveryPersons = adminService.getAllDeliveryPersons();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deliveryPersons", deliveryPersons);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch delivery persons: " + e.getMessage()));
        }
    }

    /**
     * Get delivery person by ID
     */
    @GetMapping("/delivery-persons/{id}")
    public ResponseEntity<?> getDeliveryPersonById(@PathVariable Long id) {
        try {
            DeliveryPerson deliveryPerson = adminService.getDeliveryPersonById(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deliveryPerson", deliveryPerson);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch delivery person: " + e.getMessage()));
        }
    }

    /**
     * Get delivery person documents
     */
    @GetMapping("/delivery-persons/{id}/documents")
    public ResponseEntity<?> getDeliveryPersonDocuments(@PathVariable Long id) {
        try {
            List<DeliveryPersonDocument> documents = adminService.getDeliveryPersonDocuments(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documents", documents);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch documents: " + e.getMessage()));
        }
    }

    /**
     * Get documents pending review
     */
    @GetMapping("/documents/pending-review")
    public ResponseEntity<?> getDocumentsPendingReview() {
        try {
            List<DeliveryPersonDocument> documents = adminService.getDocumentsPendingReview();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documents", documents);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch pending documents: " + e.getMessage()));
        }
    }

    /**
     * Approve document
     */
    @PostMapping("/documents/{documentId}/approve")
    public ResponseEntity<?> approveDocument(@PathVariable Long documentId, @RequestBody Map<String, Object> request) {
        try {
            Long adminId = request.containsKey("adminId") ? ((Number) request.get("adminId")).longValue() : null;

            DeliveryPersonDocument document = adminService.approveDocument(documentId, adminId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Document approved successfully");
            response.put("document", document);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to approve document: " + e.getMessage()));
        }
    }

    /**
     * Reject document
     */
    @PostMapping("/documents/{documentId}/reject")
    public ResponseEntity<?> rejectDocument(@PathVariable Long documentId, @RequestBody Map<String, Object> request) {
        try {
            String remarks = (String) request.get("remarks");
            Object adminIdObj = request.get("adminId");
            Long adminId = (adminIdObj != null) ? ((Number) adminIdObj).longValue() : null;

            if (remarks == null || remarks.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Remarks are required for rejection"));
            }

            DeliveryPersonDocument document = adminService.rejectDocument(documentId, remarks, adminId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Document rejected successfully");
            response.put("document", document);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reject document: " + e.getMessage()));
        }
    }

    /**
     * Request document re-upload
     */
    @PostMapping("/documents/{documentId}/request-reupload")
    public ResponseEntity<?> requestDocumentReupload(@PathVariable Long documentId,
            @RequestBody Map<String, Object> request) {
        try {
            String remarks = (String) request.get("remarks");
            Object adminIdObj = request.get("adminId");
            Long adminId = (adminIdObj != null) ? ((Number) adminIdObj).longValue() : null;

            if (remarks == null || remarks.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Remarks are required for re-upload request"));
            }

            DeliveryPersonDocument document = adminService.requestDocumentReupload(documentId, remarks, adminId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Re-upload requested successfully");
            response.put("document", document);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to request re-upload: " + e.getMessage()));
        }
    }

    /**
     * Get available delivery persons for order assignment
     */
    @GetMapping("/delivery-persons/available")
    public ResponseEntity<?> getAvailableDeliveryPersons() {
        try {
            List<DeliveryPerson> deliveryPersons = adminService.getAvailableDeliveryPersons();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deliveryPersons", deliveryPersons);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch available delivery persons: " + e.getMessage()));
        }
    }

    /**
     * Get pending orders for assignment
     */
    @GetMapping("/orders/pending-assignment")
    public ResponseEntity<?> getPendingOrdersForAssignment() {
        try {
            List<DeliveryOrder> orders = adminService.getPendingOrdersForAssignment();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orders", orders);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch pending orders: " + e.getMessage()));
        }
    }

    /**
     * Generate delivery OTP for picked up order
     */
    @PostMapping("/orders/{orderId}/generate-delivery-otp")
    public ResponseEntity<?> generateDeliveryOtp(@PathVariable Long orderId) {
        try {
            DeliveryOrder order = adminService.generateDeliveryOtp(orderId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Delivery OTP generated successfully");
            response.put("deliveryOtp", order.getDeliveryOtp());
            response.put("order", order);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate delivery OTP: " + e.getMessage()));
        }
    }

    /**
     * Get all delivery orders
     */
    @GetMapping("/delivery-orders")
    public ResponseEntity<?> getAllDeliveryOrders() {
        try {
            List<DeliveryOrder> orders = adminService.getAllOrders();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orders", orders);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch delivery orders: " + e.getMessage()));
        }
    }

    /**
     * Get delivery orders by status
     */
    @GetMapping("/delivery-orders/status/{status}")
    public ResponseEntity<?> getDeliveryOrdersByStatus(@PathVariable String status) {
        try {
            DeliveryOrder.OrderStatus orderStatus = DeliveryOrder.OrderStatus.valueOf(status.toUpperCase());
            List<DeliveryOrder> orders = adminService.getOrdersByStatus(orderStatus);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orders", orders);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid order status"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch delivery orders: " + e.getMessage()));
        }
    }

    /**
     * Update delivery person status (activate/deactivate)
     */
    @PutMapping("/delivery-persons/{id}/status")
    public ResponseEntity<?> updateDeliveryPersonStatus(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            Object statusObj = request.get("isActive");
            if (statusObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Status is required"));
            }
            Boolean isActive = Boolean.valueOf(statusObj.toString());
            DeliveryPerson deliveryPerson = adminService.updateDeliveryPersonStatus(id, isActive);
            return ResponseEntity
                    .ok(Map.of("success", true, "message", "Status updated", "deliveryPerson", deliveryPerson));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Approve delivery person manually
     */
    @PostMapping("/delivery-persons/{id}/approve")
    public ResponseEntity<?> approveDeliveryPerson(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Long adminId = request.containsKey("adminId") ? ((Number) request.get("adminId")).longValue() : null;
            DeliveryPerson deliveryPerson = adminService.approveDeliveryPerson(id, adminId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Approved", "deliveryPerson", deliveryPerson));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reject delivery person
     */
    @PostMapping("/delivery-persons/{id}/reject")
    public ResponseEntity<?> rejectDeliveryPerson(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Long adminId = request.containsKey("adminId") ? ((Number) request.get("adminId")).longValue() : null;
            String remarks = (String) request.get("remarks");
            DeliveryPerson deliveryPerson = adminService.rejectDeliveryPerson(id, adminId, remarks);
            return ResponseEntity.ok(Map.of("success", true, "message", "Rejected", "deliveryPerson", deliveryPerson));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Approve profile photo
     */
    @PostMapping("/delivery-persons/{id}/approve-photo")
    public ResponseEntity<?> approveProfilePhoto(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Long adminId = request.containsKey("adminId") ? ((Number) request.get("adminId")).longValue() : null;
            DeliveryPerson deliveryPerson = adminService.approveProfilePhoto(id, adminId);
            return ResponseEntity
                    .ok(Map.of("success", true, "message", "Photo approved", "deliveryPerson", deliveryPerson));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reject profile photo
     */
    @PostMapping("/delivery-persons/{id}/reject-photo")
    public ResponseEntity<?> rejectProfilePhoto(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Long adminId = request.containsKey("adminId") ? ((Number) request.get("adminId")).longValue() : null;
            String remarks = (String) request.get("remarks");
            DeliveryPerson deliveryPerson = adminService.rejectProfilePhoto(id, remarks, adminId);
            return ResponseEntity
                    .ok(Map.of("success", true, "message", "Photo rejected", "deliveryPerson", deliveryPerson));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Request profile photo re-upload
     */
    @PostMapping("/delivery-persons/{id}/request-photo-reupload")
    public ResponseEntity<?> requestProfilePhotoReupload(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            Long adminId = request.containsKey("adminId") ? ((Number) request.get("adminId")).longValue() : null;
            String remarks = (String) request.get("remarks");
            DeliveryPerson deliveryPerson = adminService.requestProfilePhotoReupload(id, remarks, adminId);
            return ResponseEntity
                    .ok(Map.of("success", true, "message", "Re-upload requested", "deliveryPerson", deliveryPerson));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/income/summary")
    public ResponseEntity<?> getIncomeSummary() {
        try {
            return ResponseEntity.ok(Map.of("success", true, "summary", adminService.getIncomeSummary()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch income summary: " + e.getMessage()));
        }
    }

    @GetMapping("/income/transactions")
    public ResponseEntity<?> getAllPayments() {
        try {
            return ResponseEntity.ok(Map.of("success", true, "transactions", adminService.getAllPayments()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch transactions: " + e.getMessage()));
        }
    }

    @GetMapping("/ratings")

    public ResponseEntity<?> getAllRatings() {
        try {
            List<ProductRating> ratings = adminService.getAllRatings();
            return ResponseEntity.ok(Map.of("success", true, "ratings", ratings));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch ratings: " + e.getMessage()));
        }
    }

    @DeleteMapping("/ratings/{id}")
    public ResponseEntity<?> deleteRating(@PathVariable Long id) {
        try {
            adminService.deleteRating(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Rating deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete rating: " + e.getMessage()));
        }
    }

}
