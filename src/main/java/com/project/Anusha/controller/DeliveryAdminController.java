package com.project.Anusha.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.DeliveryPersonDocument;
import com.project.Anusha.service.AdminService;
import com.project.Anusha.service.DeliveryPersonService;

@RestController
@RequestMapping("/api/delivery-admin")
@CrossOrigin(origins = "*")
public class DeliveryAdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private DeliveryPersonService deliveryPersonService;

    @Autowired
    private com.project.Anusha.service.DeliveryOnboardingService deliveryOnboardingService;

    /**
     * Get dashboard statistics
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        try {
            Map<String, Object> statistics = adminService.getDashboardStatistics();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", statistics);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch dashboard: " + e.getMessage()));
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
     * Get delivery persons pending approval
     */
    @GetMapping("/delivery-persons/pending-approval")
    public ResponseEntity<?> getDeliveryPersonsPendingApproval() {
        try {
            List<DeliveryPerson> deliveryPersons = adminService.getDeliveryPersonsPendingApproval();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("pendingDeliveryPersons", deliveryPersons);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch pending approvals: " + e.getMessage()));
        }
    }

    /**
     * Get available delivery persons (online and approved)
     */
    @GetMapping("/delivery-persons/available")
    public ResponseEntity<?> getAvailableDeliveryPersons() {
        try {
            List<DeliveryPerson> deliveryPersons = adminService.getAvailableDeliveryPersons();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("availableDeliveryPersons", deliveryPersons);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch available delivery persons: " + e.getMessage()));
        }
    }

    /**
     * Get delivery person by ID with documents
     */
    @GetMapping("/delivery-persons/{id}")
    public ResponseEntity<?> getDeliveryPersonById(@PathVariable Long id) {
        try {
            DeliveryPerson deliveryPerson = deliveryPersonService.getDeliveryPersonById(id);
            List<DeliveryPersonDocument> documents = deliveryPersonService.getDeliveryPersonDocuments(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deliveryPerson", deliveryPerson);
            response.put("documents", documents);
            response.put("onboardingStatus",
                    deliveryOnboardingService.buildOnboardingStatus(deliveryPerson, documents));

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
     * Get pending documents for review
     */
    @GetMapping("/documents/pending-review")
    public ResponseEntity<?> getPendingDocuments() {
        try {
            List<DeliveryPersonDocument> documents = adminService.getDocumentsPendingReview();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("pendingDocuments", documents);

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
            Object adminIdObj = request.get("adminId");
            if (adminIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin ID is required"));
            }
            Long adminId = ((Number) adminIdObj).longValue();

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
            Object adminIdObj = request.get("adminId");
            if (adminIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin ID is required"));
            }
            Long adminId = ((Number) adminIdObj).longValue();
            String remarks = (String) request.get("remarks");

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
            Object adminIdObj = request.get("adminId");
            if (adminIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin ID is required"));
            }
            Long adminId = ((Number) adminIdObj).longValue();
            String remarks = (String) request.get("remarks");

            DeliveryPersonDocument document = adminService.requestDocumentReupload(documentId, remarks, adminId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Document re-upload requested successfully");
            response.put("document", document);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to request document re-upload: " + e.getMessage()));
        }
    }

    /**
     * Approve delivery person manually
     */
    @PostMapping("/delivery-persons/{id}/approve")
    public ResponseEntity<?> approveDeliveryPerson(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Object adminIdObj = request.get("adminId");
            if (adminIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin ID is required"));
            }
            Long adminId = ((Number) adminIdObj).longValue();

            DeliveryPerson deliveryPerson = adminService.approveDeliveryPerson(id, adminId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Delivery person approved successfully");
            response.put("deliveryPerson", deliveryPerson);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to approve delivery person: " + e.getMessage()));
        }
    }

    /**
     * Reject delivery person
     */
    @PostMapping("/delivery-persons/{id}/reject")
    public ResponseEntity<?> rejectDeliveryPerson(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Object adminIdObj = request.get("adminId");
            if (adminIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin ID is required"));
            }
            Long adminId = ((Number) adminIdObj).longValue();
            String remarks = (String) request.get("remarks");

            DeliveryPerson deliveryPerson = adminService.rejectDeliveryPerson(id, adminId, remarks);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Delivery person rejected successfully");
            response.put("deliveryPerson", deliveryPerson);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reject delivery person: " + e.getMessage()));
        }
    }

    /**
     * Approve profile photo
     */
    @PostMapping("/delivery-persons/{id}/approve-photo")
    public ResponseEntity<?> approveProfilePhoto(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Object adminIdObj = request.get("adminId");
            if (adminIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin ID is required"));
            }
            Long adminId = ((Number) adminIdObj).longValue();

            DeliveryPerson deliveryPerson = adminService.approveProfilePhoto(id, adminId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile photo approved successfully",
                    "deliveryPerson", deliveryPerson));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to approve profile photo: " + e.getMessage()));
        }
    }

    /**
     * Reject profile photo
     */
    @PostMapping("/delivery-persons/{id}/reject-photo")
    public ResponseEntity<?> rejectProfilePhoto(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Object adminIdObj = request.get("adminId");
            if (adminIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin ID is required"));
            }
            Long adminId = ((Number) adminIdObj).longValue();
            String remarks = (String) request.get("remarks");

            DeliveryPerson deliveryPerson = adminService.rejectProfilePhoto(id, remarks, adminId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile photo rejected successfully",
                    "deliveryPerson", deliveryPerson));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reject profile photo: " + e.getMessage()));
        }
    }

    /**
     * Request profile photo re-upload
     */
    @PostMapping("/delivery-persons/{id}/request-photo-reupload")
    public ResponseEntity<?> requestProfilePhotoReupload(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            Object adminIdObj = request.get("adminId");
            if (adminIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin ID is required"));
            }
            Long adminId = ((Number) adminIdObj).longValue();
            String remarks = (String) request.get("remarks");

            DeliveryPerson deliveryPerson = adminService.requestProfilePhotoReupload(id, remarks, adminId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile photo re-upload requested successfully",
                    "deliveryPerson", deliveryPerson));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to request profile photo re-upload: " + e.getMessage()));
        }
    }

    /**
     * Admin update: personal + bank details for a delivery person
     */
    @PutMapping("/delivery-persons/{id}/details")
    public ResponseEntity<?> updateDeliveryPersonDetails(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            DeliveryPerson deliveryPerson = adminService.updateDeliveryPersonDetails(id, request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Details updated successfully",
                    "deliveryPerson", deliveryPerson));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update details: " + e.getMessage()));
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

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Delivery person status updated successfully");
            response.put("deliveryPerson", deliveryPerson);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update delivery person status: " + e.getMessage()));
        }
    }

    @Autowired
    private com.project.Anusha.service.DeliveryOrderService deliveryOrderService;

    @Autowired
    private com.project.Anusha.service.FareRuleService fareRuleService;

    @Autowired
    private com.project.Anusha.repository.DeliveryPersonRepository deliveryPersonRepository;

    @Autowired
    private com.project.Anusha.repository.StoreRepository storeRepository;

    /**
     * GET /api/delivery-admin/nearby-riders?storeId=1&radiusKm=5
     * Returns online approved riders within radiusKm of the given store (Haversine).
     */
    @GetMapping("/nearby-riders")
    public ResponseEntity<?> getNearbyRiders(
            @RequestParam Long storeId,
            @RequestParam(defaultValue = "5.0") double radiusKm) {
        try {
            com.project.Anusha.model.Store store = storeRepository.findById(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));
            if (store.getLatitude() == null || store.getLongitude() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Store has no GPS coordinates set"));
            }
            List<DeliveryPerson> riders = deliveryPersonRepository.findNearbyOnlineRiders(
                    store.getLatitude(), store.getLongitude(), radiusKm);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "storeId", storeId,
                    "radiusKm", radiusKm,
                    "count", riders.size(),
                    "riders", riders));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch nearby riders: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/delivery-admin/stores/{id}/location
     * Body: { "latitude": 17.385, "longitude": 78.486 }
     * Sets the GPS coordinates of a store for proximity queries.
     */
    @PutMapping("/stores/{id}/location")
    public ResponseEntity<?> updateStoreLocation(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        try {
            com.project.Anusha.model.Store store = storeRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Store not found: " + id));
            double lat = ((Number) body.get("latitude")).doubleValue();
            double lng = ((Number) body.get("longitude")).doubleValue();
            store.setLatitude(lat);
            store.setLongitude(lng);
            storeRepository.save(store);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Store location updated",
                    "storeId", id,
                    "latitude", lat,
                    "longitude", lng));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update store location: " + e.getMessage()));
        }
    }

    /**
     * Get all delivery orders (with status filter)
     */
    @GetMapping("/orders")
    public ResponseEntity<?> getDeliveryOrders(@RequestParam(required = false) String status) {
        try {
            List<com.project.Anusha.model.DeliveryOrder> orders;
            if (status != null && !status.isBlank()) {
                orders = deliveryOrderService.getOrdersByStatus(com.project.Anusha.model.DeliveryOrder.OrderStatus.valueOf(status.toUpperCase()));
            } else {
                orders = deliveryOrderService.getAllActiveOrders();
            }
            return ResponseEntity.ok(Map.of("success", true, "orders", orders));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch orders: " + e.getMessage()));
        }
    }

    /**
     * Manual Store Acceptance Override (Admin accepts on behalf of store)
     */
    @PostMapping("/orders/{orderNumber}/store-accept")
    public ResponseEntity<?> adminAcceptStoreOrder(@PathVariable String orderNumber, @RequestBody Map<String, String> data) {
        try {
            String remarks = data.getOrDefault("remarks", "Manual Admin Acceptance");
            deliveryOrderService.handleStoreResponse(orderNumber, "ACCEPT", remarks);
            return ResponseEntity.ok(Map.of("success", true, "message", "Order " + orderNumber + " accepted via Admin Override"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to override store acceptance: " + e.getMessage()));
        }
    }

    /**
     * GET /api/delivery-admin/fare-rules
     * Returns all fare rules (BIKE, SCOOTY, EV, AUTO, HEAVY) — ADMIN only.
     */
    @GetMapping("/fare-rules")
    public ResponseEntity<?> getFareRules() {
        try {
            List<com.project.Anusha.model.FareRule> rules = fareRuleService.getAllFareRules();
            return ResponseEntity.ok(Map.of("success", true, "fareRules", rules));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch fare rules: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/delivery-admin/fare-rules/{id}
     * Update fare rule amounts (baseFare, baseKm, perKmRate, rainSurcharge, loadingCharge, unloadingCharge).
     * Does NOT change rainActive — use the toggle endpoint for that.
     */
    @PutMapping("/fare-rules/{id}")
    public ResponseEntity<?> updateFareRule(@PathVariable Long id,
                                            @RequestBody com.project.Anusha.model.FareRule rule) {
        try {
            com.project.Anusha.model.FareRule updated = fareRuleService.updateFareRule(id, rule);
            return ResponseEntity.ok(Map.of("success", true,
                    "message", "Fare rule updated successfully", "fareRule", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update fare rule: " + e.getMessage()));
        }
    }

    /**
     * POST /api/delivery-admin/fare-rules/{vehicleType}/rain-toggle
     * Body: { "rainActive": true | false }
     *
     * Toggles rain surcharge for the given vehicle type.
     * For BIKE / SCOOTY / EV: all three are toggled together (same rain group).
     *
     * Example: POST /api/delivery-admin/fare-rules/BIKE/rain-toggle { "rainActive": true }
     */
    @PostMapping("/fare-rules/{vehicleType}/rain-toggle")
    public ResponseEntity<?> toggleRain(@PathVariable String vehicleType,
                                        @RequestBody Map<String, Object> body) {
        try {
            com.project.Anusha.model.DeliveryPerson.VehicleType vt =
                    com.project.Anusha.model.DeliveryPerson.VehicleType.valueOf(vehicleType.toUpperCase());
            boolean rainActive = Boolean.TRUE.equals(body.get("rainActive"));
            Map<String, Object> result = fareRuleService.toggleRain(vt, rainActive);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid vehicle type: " + vehicleType));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to toggle rain: " + e.getMessage()));
        }
    }

    /**
     * POST /api/delivery-admin/fare-rules/calculate
     * Body: { "vehicleType": "BIKE", "distanceKm": 9.7 }
     * Returns full fare breakdown for preview in admin panel.
     */
    @PostMapping("/fare-rules/calculate")
    public ResponseEntity<?> calculateFare(@RequestBody Map<String, Object> body) {
        try {
            String vehicleTypeStr = (String) body.get("vehicleType");
            Number distanceNum = (Number) body.get("distanceKm");
            if (vehicleTypeStr == null || distanceNum == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "vehicleType and distanceKm are required"));
            }
            com.project.Anusha.model.DeliveryPerson.VehicleType vt =
                    com.project.Anusha.model.DeliveryPerson.VehicleType.valueOf(vehicleTypeStr.toUpperCase());
            Map<String, Object> breakdown = fareRuleService.calculateFareBreakdown(vt, distanceNum.doubleValue());
            return ResponseEntity.ok(Map.of("success", true, "fareBreakdown", breakdown));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Fare calculation failed: " + e.getMessage()));
        }
    }

    /**
     * Get delivery person documents
     */
    @GetMapping("/delivery-persons/{id}/documents")
    public ResponseEntity<?> getDeliveryPersonDocuments(@PathVariable Long id) {
        try {
            List<DeliveryPersonDocument> documents = deliveryPersonService.getDeliveryPersonDocuments(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documents", documents);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch documents: " + e.getMessage()));
        }
    }
}
