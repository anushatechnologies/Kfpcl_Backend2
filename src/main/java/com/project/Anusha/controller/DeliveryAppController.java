package com.project.Anusha.controller;

import com.project.Anusha.model.DeliveryBroadcast;
import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.DeliveryPersonDocument;
import com.project.Anusha.model.OrderItem;
import com.project.Anusha.model.Payout;
import com.project.Anusha.repository.OrderRepository;
import com.project.Anusha.service.AdminService;
import com.project.Anusha.service.DeliveryPersonService;
import com.project.Anusha.service.PayoutService;
import com.project.Anusha.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/delivery-app")
@CrossOrigin(origins = "*")
public class DeliveryAppController {

    @Autowired
    private DeliveryPersonService deliveryPersonService;

    @Autowired
    private PayoutService payoutService;

    @Autowired
    private com.project.Anusha.service.DeliveryOrderService deliveryOrderService;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private com.project.Anusha.service.DeliveryOnboardingService deliveryOnboardingService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private com.project.Anusha.service.FareRuleService fareRuleService;

    @Autowired
    private OrderRepository orderRepository;

    // ─────────────────────────────────────────────────────────────────────
    // PHASE 3 — GPS Location Ping (MySQL persistence + Firebase RTDB)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * POST /api/delivery-app/location
     * Body: { "lat": 17.385, "lng": 78.486 }
     * Called every 30 s while rider is online. Updates MySQL.
     */
    @PostMapping("/location")
    public ResponseEntity<?> updateLocation(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> body) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson dp = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));
            double lat = ((Number) body.get("lat")).doubleValue();
            double lng = ((Number) body.get("lng")).doubleValue();
            deliveryOrderService.updateRiderLocation(dp.getId(), lat, lng);
            return ResponseEntity.ok(Map.of("success", true, "message", "Location updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Location update failed: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // PHASE 7 — Customer OTP + Delivery Photo
    // ─────────────────────────────────────────────────────────────────────

    /**
     * POST /api/delivery-app/orders/{orderNumber}/generate-delivery-otp
     * Generates 4-digit OTP and sends to customer via FCM.
     */
    @PostMapping("/orders/{orderNumber}/generate-delivery-otp")
    public ResponseEntity<?> generateDeliveryOtp(@PathVariable String orderNumber) {
        try {
            String otp = deliveryOrderService.generateDeliveryOtp(orderNumber);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "OTP sent to customer",
                    "otp", otp));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate OTP: " + e.getMessage()));
        }
    }

    /**
     * POST /api/delivery-app/orders/{orderNumber}/confirm-delivery  (multipart)
     * Parts: otp (required), photo (optional file)
     * Verifies OTP, stores photo in S3, marks order DELIVERED.
     */
    @PostMapping(value = "/orders/{orderNumber}/confirm-delivery",
                 consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> confirmDeliveryWithPhoto(
            @PathVariable String orderNumber,
            @RequestParam("otp") String otp,
            @RequestParam(value = "photo", required = false) org.springframework.web.multipart.MultipartFile photo) {
        try {
            String photoUrl = null;
            if (photo != null && !photo.isEmpty()) {
                photoUrl = s3Service.uploadFile(photo, "delivery-boys/delivery-photos");
            }
            com.project.Anusha.model.DeliveryOrder order =
                    deliveryOrderService.confirmOrderDeliveryWithPhoto(orderNumber, otp, photoUrl);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Order delivered successfully!",
                    "orderNumber", order.getOrderNumber(),
                    "deliveredAt", order.getDeliveredAt().toString(),
                    "photoUrl", photoUrl != null ? photoUrl : ""));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Delivery confirmation failed: " + e.getMessage()));
        }
    }

    // -------------------- ORDER MANAGEMENT --------------------

    /**
     * Get orders broadcasted to riders but not yet accepted
     */
    @GetMapping("/available-orders")
    public ResponseEntity<?> getAvailableOrders(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson dp = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            if (!dp.isApprovedByAdmin() || !dp.isOnline()) {
                return ResponseEntity.ok(Map.of("success", true, "orders", java.util.Collections.emptyList()));
            }

            List<Map<String, Object>> orders = adminService.getAvailableBroadcastsForRider(dp.getId()).stream()
                    .map(broadcast -> {
                        com.project.Anusha.model.DeliveryOrder deliveryOrder =
                                deliveryOrderService.getOrderByOrderNumber(broadcast.getOrder().getOrderNumber()).orElse(null);
                        if (deliveryOrder == null) {
                            return null;
                        }
                        Map<String, Object> item = toDeliveryOrderMap(deliveryOrder, dp);
                        item.put("broadcastId", broadcast.getId());
                        item.put("vehicleType", broadcast.getVehicleType().name());
                        item.put("expiresAt", broadcast.getExpiresAt().toString());
                        return item;
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(Map.of("success", true, "orders", orders));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch available orders: " + e.getMessage()));
        }
    }

    /**
     * Rider accepts an order (Atomic).
     * Returns a flat order map with store details so the app can navigate immediately.
     */
    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<?> acceptOrder(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long orderId) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson dp = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            com.project.Anusha.model.DeliveryOrder order = adminService.getAvailableBroadcastsForRider(dp.getId()).stream()
                    .map(broadcast -> {
                        com.project.Anusha.model.DeliveryOrder deliveryOrder =
                                deliveryOrderService.getOrderByOrderNumber(broadcast.getOrder().getOrderNumber()).orElse(null);
                        if (deliveryOrder == null || !deliveryOrder.getId().equals(orderId)) {
                            return null;
                        }
                        return broadcast;
                    })
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .map(broadcast -> adminService.acceptBroadcast(broadcast.getId(), dp.getId()))
                    .orElseGet(() -> deliveryOrderService.acceptOrder(orderId, dp.getId()));

            // Build flat response so store name/phone/coords are available right away
            Map<String, Object> orderMap = toDeliveryOrderMap(order, dp);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Order accepted successfully",
                    "order", orderMap));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to accept order: " + e.getMessage()));
        }
    }

    /**
     * Mark order as reached store
     */
    @PostMapping("/orders/{orderNumber}/arrived")
    public ResponseEntity<?> markArrived(@PathVariable String orderNumber) {
        try {
            deliveryOrderService.markArrivedAtStore(orderNumber);
            return ResponseEntity.ok(Map.of("success", true, "message", "Status updated to Reached Store"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update status: " + e.getMessage()));
        }
    }

    /**
     * Verify pickup OTP — calculates store→customer distance and fare, marks OUT_FOR_DELIVERY.
     */
    @PostMapping("/orders/{orderNumber}/picked-up")
    public ResponseEntity<?> verifyPickup(
            @PathVariable String orderNumber,
            @RequestBody Map<String, String> data) {
        try {
            String otp = data.get("otp");
            if (otp == null) return ResponseEntity.badRequest().body(Map.of("error", "OTP is required"));

            com.project.Anusha.model.DeliveryOrder order = deliveryOrderService.verifyPickupOtpAndPickedUp(orderNumber, otp);

            // Return flat map with fare breakdown so the app can show it immediately
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Pickup verified — heading to customer!");
            result.put("status", order.getStatus().name());
            result.put("distanceKm", order.getDistanceKm());
            result.put("deliveryFee", order.getDeliveryFee());
            result.put("orderNumber", order.getOrderNumber());
            result.put("customerName", order.getCustomerName());
            result.put("deliveryAddress", order.getDeliveryAddress());
            result.put("deliveryLatitude", order.getDeliveryLatitude());
            result.put("deliveryLongitude", order.getDeliveryLongitude());

            // Fare breakdown if rider has a vehicle type
            if (order.getDeliveryPerson() != null && order.getDeliveryPerson().getVehicleType() != null
                    && order.getDistanceKm() != null) {
                try {
                    Map<String, Object> breakdown = fareRuleService.calculateFareBreakdown(
                            order.getDeliveryPerson().getVehicleType(), order.getDistanceKm());
                    result.put("fareBreakdown", breakdown);
                } catch (Exception ignored) { /* fare rule missing — skip breakdown */ }
            }

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to verify pickup: " + e.getMessage()));
        }
    }

    /**
     * POST /api/delivery-app/orders/{orderNumber}/rate
     * Body: { "stars": 4, "feedback": "Great service!" }
     * Customer (or rider self-review) submits a 1–5 star rating after delivery.
     * Can only be called once per order (idempotent guard in service).
     */
    @PostMapping("/orders/{orderNumber}/rate")
    public ResponseEntity<?> rateDelivery(
            @PathVariable String orderNumber,
            @RequestBody Map<String, Object> body) {
        try {
            Object starsObj = body.get("stars");
            if (starsObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "stars field is required (1–5)"));
            }
            int stars = ((Number) starsObj).intValue();
            String feedback = body.get("feedback") instanceof String ? (String) body.get("feedback") : null;

            com.project.Anusha.model.DeliveryOrder order =
                    deliveryOrderService.rateDelivery(orderNumber, stars, feedback);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rating submitted successfully",
                    "orderNumber", order.getOrderNumber(),
                    "stars", order.getCustomerRating()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to submit rating: " + e.getMessage()));
        }
    }

    /**
     * Final delivery confirmation
     */
    @PostMapping("/orders/{orderNumber}/delivered")
    public ResponseEntity<?> confirmDelivered(@PathVariable String orderNumber) {
        try {
            com.project.Anusha.model.DeliveryOrder order = deliveryOrderService.confirmOrderDelivered(orderNumber);
            return ResponseEntity.ok(Map.of("success", true, "message", "Order delivered successfully", "order", order));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to confirm delivery: " + e.getMessage()));
        }
    }

    // -------------------- AUTHENTICATED ENDPOINTS (use principal)
    // --------------------

    /**
     * Get own status (with documents and completion status)
     */
    @GetMapping("/status")
    public ResponseEntity<?> getMyStatus(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson deliveryPerson = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            List<DeliveryPersonDocument> documents = deliveryPersonService
                    .getDeliveryPersonDocuments(deliveryPerson.getId());
            boolean allDocumentsApproved = deliveryPersonService.areAllDocumentsApproved(deliveryPerson.getId());
            Map<String, Object> onboardingStatus =
                    deliveryOnboardingService.buildOnboardingStatus(deliveryPerson, documents);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deliveryPerson", deliveryPerson);
            response.put("documents", documents);
            response.put("allDocumentsApproved", allDocumentsApproved);
            response.put("completionStatus", onboardingStatus);
            response.put("onboardingStatus", onboardingStatus);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch status: " + e.getMessage()));
        }
    }

    /**
     * Update own online status
     */
    @PutMapping("/online-status")
    public ResponseEntity<?> updateOnlineStatus(@AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Boolean> statusData) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson deliveryPerson = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            Boolean isOnline = statusData.get("isOnline");
            if (isOnline == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Online status is required"));
            }

            // Block going online if not approved by admin
            if (isOnline && !deliveryPerson.isApprovedByAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "success", false,
                                "error",
                                "You cannot go online until your account is approved by the admin. Please ensure all documents are uploaded and approved.",
                                "approvalStatus", deliveryPerson.getApprovalStatus().name()));
            }

            DeliveryPerson updated = deliveryPersonService.updateOnlineStatus(deliveryPerson.getId(), isOnline);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Online status updated successfully",
                    "isOnline", updated.isOnline()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update online status: " + e.getMessage()));
        }
    }

    /**
     * Update own profile details (Name, Last Name, Profile photo URL)
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> profileData) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson dp = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            String firstName = profileData.get("firstName");
            String lastName = profileData.get("lastName");
            String profilePhotoUrl = profileData.get("profilePhotoUrl");

            DeliveryPerson updated = deliveryPersonService.updateProfile(dp.getId(), firstName, lastName,
                    profilePhotoUrl);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile updated successfully",
                    "deliveryPerson", Map.of(
                            "id", updated.getId(),
                            "firstName", updated.getFirstName(),
                            "lastName", updated.getLastName(),
                            "profilePhotoUrl", updated.getProfilePhotoUrl() != null ? updated.getProfilePhotoUrl() : "",
                            "profilePhotoStatus", updated.getProfilePhotoStatus().name())));
        } catch (IllegalStateException e) {
            // Profile photo locked after admin approval
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", e.getMessage(), "locked", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update profile: " + e.getMessage()));
        }
    }

    /**
     * Update own profile photo (Multipart File)
     */
    @PostMapping(value = "/profile-photo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProfilePhoto(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson dp = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            // Check if photo is already approved (locked)
            if (dp.getProfilePhotoStatus() == com.project.Anusha.model.DeliveryPersonDocument.DocumentStatus.APPROVED) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "error", "Cannot change profile photo after admin approval"));
            }

            // Upload to S3 — consistent folder with signup upload
            String photoUrl = s3Service.uploadFile(file, "delivery-boys/profile-photos");

            // Save in DB (resets status to PENDING/RE-UPLOADED)
            DeliveryPerson updated = deliveryPersonService.updateProfile(dp.getId(), null, null, photoUrl);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile photo uploaded successfully. Awaiting admin approval.",
                    "profilePhotoUrl", updated.getProfilePhotoUrl(),
                    "profilePhotoStatus", updated.getProfilePhotoStatus()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload profile photo: " + e.getMessage()));
        }
    }

    /**
     * Get own dashboard data
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson deliveryPerson = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            // Compute total login hours: accumulated minutes + current session if online
            long accumulatedMinutes = deliveryPerson.getTotalLoginMinutes();
            if (deliveryPerson.isOnline() && deliveryPerson.getLastActiveTime() != null) {
                long sessionMinutes = java.time.Duration
                        .between(deliveryPerson.getLastActiveTime(), java.time.LocalDateTime.now())
                        .toMinutes();
                accumulatedMinutes += Math.max(0, sessionMinutes);
            }
            double loginHours = accumulatedMinutes / 60.0;

            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("totalEarnings", deliveryPerson.getTotalEarnings());
            dashboard.put("weeklyEarnings", deliveryPerson.getWeeklyEarnings());
            dashboard.put("totalCompletedOrders", deliveryPerson.getTotalCompletedOrders());
            dashboard.put("weeklyCompletedOrders", deliveryPerson.getWeeklyCompletedOrders());
            dashboard.put("isOnline", deliveryPerson.isOnline());
            dashboard.put("isVerified", deliveryPerson.isVerified());
            dashboard.put("isApprovedByAdmin", deliveryPerson.isApprovedByAdmin());
            dashboard.put("vehicleType", deliveryPerson.getVehicleType());
            dashboard.put("vehicleModel", deliveryPerson.getVehicleModel());
            dashboard.put("registrationNumber", deliveryPerson.getRegistrationNumber());
            dashboard.put("totalLoginMinutes", accumulatedMinutes);
            dashboard.put("loginHours", loginHours);
            dashboard.put("averageRating", deliveryPerson.getAverageRating());
            dashboard.put("totalRatings", deliveryPerson.getTotalRatings());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "dashboard", dashboard));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch dashboard data: " + e.getMessage()));
        }
    }

    /**
     * Update vehicle information (already using principal)
     */
    @PutMapping("/vehicle")
    public ResponseEntity<?> updateVehicleInfo(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> vehicleData) {

        String phoneNumber = userDetails.getUsername(); // phone number from JWT

        try {
            String vehicleTypeStr = (String) vehicleData.get("vehicleType");
            String vehicleModel = (String) vehicleData.get("vehicleModel");
            String registrationNumber = (String) vehicleData.get("registrationNumber");

            if (vehicleTypeStr == null || vehicleModel == null || registrationNumber == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "All vehicle fields are required"));
            }

            DeliveryPerson.VehicleType vehicleType = DeliveryPerson.VehicleType.valueOf(vehicleTypeStr.toUpperCase());

            DeliveryPerson deliveryPerson = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            if (deliveryPerson.getApprovalStatus() == DeliveryPerson.ApprovalStatus.APPROVED) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "locked", true,
                                "error", "Vehicle details are locked after admin approval. Contact support to make changes."));
            }

            DeliveryPerson updatedPerson = deliveryPersonService.updateVehicleInfo(
                    deliveryPerson.getId(), vehicleType, vehicleModel, registrationNumber);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vehicle information updated successfully",
                    "deliveryPerson", Map.of(
                            "id", updatedPerson.getId(),
                            "vehicleType", updatedPerson.getVehicleType().name(),
                            "vehicleModel", updatedPerson.getVehicleModel(),
                            "registrationNumber", updatedPerson.getRegistrationNumber())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to update vehicle information: " + e.getMessage()));
        }
    }

    // -------------------- PUBLIC / ADMIN ENDPOINTS (still require authentication)
    // --------------------

    /**
     * Get delivery person by phone number – may be used by admin or for
     * self‑lookup.
     * (Can be restricted to admin later if needed.)
     */
    @GetMapping("/phone/{phoneNumber}")
    public ResponseEntity<?> getDeliveryPersonByPhone(@PathVariable String phoneNumber) {
        try {
            Optional<DeliveryPerson> deliveryPersonOpt = deliveryPersonService
                    .getDeliveryPersonByPhoneNumber(phoneNumber);

            if (deliveryPersonOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Delivery person not found"));
            }

            DeliveryPerson deliveryPerson = deliveryPersonOpt.get();
            List<DeliveryPersonDocument> documents = deliveryPersonService
                    .getDeliveryPersonDocuments(deliveryPerson.getId());
            boolean allDocumentsApproved = deliveryPersonService.areAllDocumentsApproved(deliveryPerson.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deliveryPerson", deliveryPerson);
            response.put("documents", documents);
            response.put("allDocumentsApproved", allDocumentsApproved);
            response.put("onboardingStatus",
                    deliveryOnboardingService.buildOnboardingStatus(deliveryPerson, documents));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch delivery person: " + e.getMessage()));
        }
    }

    /**
     * Update bank account details
     */
    @PutMapping("/bank-details")
    public ResponseEntity<?> updateBankDetails(@AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> bankData) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson dp = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            String accountName = bankData.get("accountName");
            String accountNumber = bankData.get("accountNumber");
            String bankName = bankData.get("bankName");
            String ifscCode = bankData.get("ifscCode");

            if (accountName == null || accountNumber == null || bankName == null || ifscCode == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "All bank fields are required"));
            }

            if (dp.getApprovalStatus() == DeliveryPerson.ApprovalStatus.APPROVED) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "locked", true,
                                "error", "Bank details are locked after admin approval. Contact support to make changes."));
            }

            DeliveryPerson updated = deliveryPersonService.updateBankDetails(dp.getId(), accountName, accountNumber,
                    bankName, ifscCode);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Bank details updated successfully",
                    "deliveryPerson", Map.of(
                            "accountName", updated.getAccountName(),
                            "accountNumber", updated.getAccountNumber(),
                            "bankName", updated.getBankName(),
                            "ifscCode", updated.getIfscCode())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update bank details: " + e.getMessage()));
        }
    }

    /**
     * Get payout records for self
     */
    @GetMapping("/payouts")
    public ResponseEntity<?> getMyPayouts(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson dp = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            List<Payout> payouts = payoutService.getPayoutsForDeliveryPerson(dp.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "payouts", payouts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch payouts: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // BROADCAST — First-Come-First-Serve order assignment
    // ─────────────────────────────────────────────────────────────────────

    /**
     * GET /api/delivery-app/broadcasts/available
     * Returns all active (non-expired, not yet accepted) broadcasts matching
     * this rider's vehicle type. The delivery app polls this to show available orders.
     */
    @GetMapping("/broadcasts/available")
    public ResponseEntity<?> getAvailableBroadcasts(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson dp = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            if (!dp.isApprovedByAdmin() || !dp.isOnline()) {
                return ResponseEntity.ok(Map.of("success", true, "broadcasts", java.util.Collections.emptyList()));
            }

            List<DeliveryBroadcast> broadcasts = adminService.getAvailableBroadcastsForRider(dp.getId());

            List<Map<String, Object>> result = broadcasts.stream()
                    .map(b -> {
                        com.project.Anusha.model.DeliveryOrder deliveryOrder =
                                deliveryOrderService.getOrderByOrderNumber(b.getOrder().getOrderNumber()).orElse(null);
                        if (deliveryOrder == null) {
                            return null;
                        }
                        Map<String, Object> item = toDeliveryOrderMap(deliveryOrder, dp);
                        item.put("broadcastId", b.getId());
                        item.put("vehicleType", b.getVehicleType().name());
                        item.put("expiresAt", b.getExpiresAt().toString());
                        return item;
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(Map.of("success", true, "broadcasts", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch broadcasts: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // FARE RULES — Rider's fare structure based on their vehicle type
    // ─────────────────────────────────────────────────────────────────────

    /**
     * GET /api/delivery-app/fare-rule
     * Returns the fare rule for the authenticated rider's vehicle type.
     */
    @GetMapping("/fare-rule")
    public ResponseEntity<?> getMyFareRule(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson dp = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));
            if (dp.getVehicleType() == null) {
                return ResponseEntity.ok(Map.of("success", true, "fareRule", null));
            }
            com.project.Anusha.model.FareRule rule = fareRuleService.getFareRuleByVehicleType(dp.getVehicleType());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "vehicleType", dp.getVehicleType().name(),
                    "fareRule", rule));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch fare rule: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // BANNERS — Home screen slider content for delivery partner app
    // ─────────────────────────────────────────────────────────────────────

    @Autowired
    private com.project.Anusha.repository.BannerRepository bannerRepository;

    /**
     * GET /api/delivery-app/banners
     * Returns all active banners ordered by displayOrder for the home screen carousel.
     */
    @GetMapping("/banners")
    public ResponseEntity<?> getActiveBanners() {
        try {
            // Fetch only banners targeting DELIVERY or BOTH
            List<com.project.Anusha.model.Banner> banners =
                    bannerRepository.findActiveBannersForApp("DELIVERY");
            return ResponseEntity.ok(Map.of("success", true, "banners", banners));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch banners: " + e.getMessage()));
        }
    }

    /**
     * POST /api/delivery-app/broadcasts/{broadcastId}/accept
     * First-come-first-serve: the first rider to call this wins the order assignment.
     * Others will receive a push notification that the order was taken.
     */
    @PostMapping("/broadcasts/{broadcastId}/accept")
    public ResponseEntity<?> acceptBroadcast(@AuthenticationPrincipal UserDetails userDetails,
                                             @PathVariable Long broadcastId) {
        try {
            String phoneNumber = userDetails.getUsername();
            DeliveryPerson dp = deliveryPersonService.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

            com.project.Anusha.model.DeliveryOrder deliveryOrder =
                    adminService.acceptBroadcast(broadcastId, dp.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Order accepted! Check your active orders.",
                    "orderNumber", deliveryOrder.getOrderNumber()
            ));
        } catch (IllegalStateException e) {
            // Order already taken by someone else
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to accept broadcast: " + e.getMessage()));
        }
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Double roundDistance(Double distanceKm) {
        if (distanceKm == null) {
            return null;
        }
        return Math.round(distanceKm * 100.0) / 100.0;
    }

    private void appendRouteMetrics(Map<String, Object> orderMap,
                                    com.project.Anusha.model.DeliveryOrder order,
                                    DeliveryPerson rider) {
        Double storeToCustomerDistanceKm = order.getDistanceKm();
        if (storeToCustomerDistanceKm == null
                && order.getPickupLatitude() != null
                && order.getPickupLongitude() != null
                && order.getDeliveryLatitude() != null
                && order.getDeliveryLongitude() != null) {
            storeToCustomerDistanceKm = roundDistance(haversineKm(
                    order.getPickupLatitude(),
                    order.getPickupLongitude(),
                    order.getDeliveryLatitude(),
                    order.getDeliveryLongitude()));
        }

        orderMap.put("distanceKm", storeToCustomerDistanceKm);
        orderMap.put("storeToCustomerDistanceKm", storeToCustomerDistanceKm);

        if (rider != null
                && rider.getLatitude() != null
                && rider.getLongitude() != null
                && order.getPickupLatitude() != null
                && order.getPickupLongitude() != null) {
            Double riderToStoreDistanceKm = roundDistance(haversineKm(
                    rider.getLatitude(),
                    rider.getLongitude(),
                    order.getPickupLatitude(),
                    order.getPickupLongitude()));
            orderMap.put("riderToStoreDistanceKm", riderToStoreDistanceKm);

            if (storeToCustomerDistanceKm != null) {
                orderMap.put("totalRouteDistanceKm", roundDistance(riderToStoreDistanceKm + storeToCustomerDistanceKm));
            } else {
                orderMap.put("totalRouteDistanceKm", riderToStoreDistanceKm);
            }
        } else if (storeToCustomerDistanceKm != null) {
            orderMap.put("totalRouteDistanceKm", storeToCustomerDistanceKm);
        }
    }

    private Map<String, Object> toDeliveryOrderMap(com.project.Anusha.model.DeliveryOrder order) {
        return toDeliveryOrderMap(order, null);
    }

    private Map<String, Object> toDeliveryOrderMap(com.project.Anusha.model.DeliveryOrder order, DeliveryPerson rider) {
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("id", order.getId());
        orderMap.put("orderId", order.getId());
        orderMap.put("orderNumber", order.getOrderNumber());
        orderMap.put("customerName", order.getCustomerName());
        orderMap.put("customerPhone", order.getCustomerPhone());
        orderMap.put("pickupAddress", order.getPickupAddress());
        orderMap.put("deliveryAddress", order.getDeliveryAddress());
        orderMap.put("orderAmount", order.getOrderAmount());
        orderMap.put("grandTotal", order.getOrderAmount());
        orderMap.put("deliveryFee", order.getDeliveryFee());
        orderMap.put("status", order.getStatus().name());
        orderMap.put("paymentType", order.getPaymentType() != null ? order.getPaymentType().name() : null);
        orderMap.put("deliveryOtp", order.getDeliveryOtp() != null ? order.getDeliveryOtp() : "");
        orderMap.put("deliveryOtpExpiry", order.getDeliveryOtpExpiry());
        orderMap.put("assignedAt", order.getAssignedAt());
        orderMap.put("acceptedAt", order.getAcceptedAt());
        orderMap.put("pickedUpAt", order.getPickedUpAt());
        orderMap.put("deliveredAt", order.getDeliveredAt());
        orderMap.put("deliveryOtpVerifiedAt", order.getDeliveryOtpVerifiedAt());
        orderMap.put("deliveryPhotoUrl", order.getDeliveryPhotoUrl());
        orderMap.put("pickupLatitude", order.getPickupLatitude());
        orderMap.put("pickupLongitude", order.getPickupLongitude());
        orderMap.put("deliveryLatitude", order.getDeliveryLatitude());
        orderMap.put("deliveryLongitude", order.getDeliveryLongitude());

        appendRouteMetrics(orderMap, order, rider);

        if (order.getStore() != null) {
            Map<String, Object> storeMap = new HashMap<>();
            storeMap.put("name", order.getStore().getName());
            storeMap.put("address", order.getStore().getAddress());
            storeMap.put("phoneNumber", order.getStore().getPhoneNumber());
            storeMap.put("latitude", order.getStore().getLatitude());
            storeMap.put("longitude", order.getStore().getLongitude());
            orderMap.put("store", storeMap);
            orderMap.put("storeName", order.getStore().getName());
            orderMap.put("storePhone", order.getStore().getPhoneNumber());
        }

        List<Map<String, Object>> items = new java.util.ArrayList<>();
        orderRepository.findByOrderNumber(order.getOrderNumber()).ifPresent(customerOrder -> {
            for (OrderItem item : customerOrder.getItems()) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("name", item.getProductName());
                itemMap.put("variantName", item.getVariantName());
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("price", item.getUnitPrice());
                itemMap.put("totalPrice", item.getTotalPrice());
                itemMap.put("imageUrl", item.getImageUrl());
                items.add(itemMap);
            }
        });
        orderMap.put("items", items);
        orderMap.put("itemsCount", items.size());

        return orderMap;
    }
}
