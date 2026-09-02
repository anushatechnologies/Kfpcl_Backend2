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
import com.project.Anusha.service.DeliveryPersonService;

/**
 * Non-auth delivery person endpoints (vehicle info, documents, dashboard).
 * Authentication (signup / login) is handled by {@link DeliveryAuthController}.
 * All endpoints here require a valid JWT (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/delivery-person")
@CrossOrigin(origins = "*")
public class DeliveryPersonController {

    @Autowired
    private DeliveryPersonService deliveryPersonService;

    @Autowired
    private com.project.Anusha.service.DeliveryOnboardingService deliveryOnboardingService;

    /**
     * Update vehicle information for a delivery person.
     */
    @PutMapping("/{id}/vehicle")
    public ResponseEntity<?> updateVehicleInfo(@PathVariable Long id,
                                               @RequestBody Map<String, Object> vehicleData) {
        try {
            String vehicleTypeStr = (String) vehicleData.get("vehicleType");
            String vehicleModel = (String) vehicleData.get("vehicleModel");
            String registrationNumber = (String) vehicleData.get("registrationNumber");

            if (vehicleTypeStr == null || vehicleModel == null || registrationNumber == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "All vehicle fields are required"));
            }

            DeliveryPerson.VehicleType vehicleType =
                    DeliveryPerson.VehicleType.valueOf(vehicleTypeStr.toUpperCase());

            DeliveryPerson deliveryPerson = deliveryPersonService
                    .updateVehicleInfo(id, vehicleType, vehicleModel, registrationNumber);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vehicle information updated successfully");
            response.put("deliveryPerson", Map.of(
                    "id", deliveryPerson.getId(),
                    "vehicleType", deliveryPerson.getVehicleType().name(),
                    "vehicleModel", deliveryPerson.getVehicleModel(),
                    "registrationNumber", deliveryPerson.getRegistrationNumber()
            ));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update vehicle information: " + e.getMessage()));
        }
    }

    /**
     * Get delivery person documents.
     */
    @GetMapping("/{id}/documents")
    public ResponseEntity<?> getDocuments(@PathVariable Long id) {
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

    /**
     * Update online / offline status.
     */
    @PutMapping("/{id}/online-status")
    public ResponseEntity<?> updateOnlineStatus(@PathVariable Long id,
                                                @RequestBody Map<String, Boolean> statusData) {
        try {
            Boolean isOnline = statusData.get("isOnline");
            if (isOnline == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Online status is required"));
            }

            DeliveryPerson dp = deliveryPersonService.getDeliveryPersonById(id);

            // Block going online if not approved by admin
            if (isOnline && !dp.isApprovedByAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                            "success", false,
                            "error", "You cannot go online until your account is approved by the admin. Please ensure all documents are uploaded and approved.",
                            "approvalStatus", dp.getApprovalStatus().name()
                        ));
            }

            DeliveryPerson deliveryPerson = deliveryPersonService.updateOnlineStatus(id, isOnline);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Online status updated successfully");
            response.put("isOnline", deliveryPerson.isOnline());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update online status: " + e.getMessage()));
        }
    }

    /**
     * Get delivery person dashboard stats.
     */
    @GetMapping("/{id}/dashboard")
    public ResponseEntity<?> getDashboard(@PathVariable Long id) {
        try {
            DeliveryPerson dp = deliveryPersonService.getDeliveryPersonById(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("dashboard", Map.of(
                    "totalEarnings", dp.getTotalEarnings(),
                    "weeklyEarnings", dp.getWeeklyEarnings(),
                    "totalCompletedOrders", dp.getTotalCompletedOrders(),
                    "weeklyCompletedOrders", dp.getWeeklyCompletedOrders(),
                    "isOnline", dp.isOnline(),
                    "isVerified", dp.isVerified(),
                    "isApprovedByAdmin", dp.isApprovedByAdmin(),
                    "approvalStatus", dp.getApprovalStatus().name()
            ));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch dashboard data: " + e.getMessage()));
        }
    }

    /**
     * Get delivery person by ID (with documents).
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDeliveryPerson(@PathVariable Long id) {
        try {
            DeliveryPerson dp = deliveryPersonService.getDeliveryPersonById(id);

            Map<String, Object> dpMap = new HashMap<>();
            dpMap.put("id", dp.getId());
            dpMap.put("firstName", dp.getFirstName());
            dpMap.put("lastName", dp.getLastName());
            dpMap.put("phoneNumber", dp.getPhoneNumber());
            dpMap.put("vehicleType", dp.getVehicleType().name());
            dpMap.put("vehicleModel", dp.getVehicleModel());
            dpMap.put("registrationNumber", dp.getRegistrationNumber());
            dpMap.put("isOnline", dp.isOnline());
            dpMap.put("isVerified", dp.isVerified());
            dpMap.put("isApprovedByAdmin", dp.isApprovedByAdmin());
            dpMap.put("approvalStatus", dp.getApprovalStatus().name());
            dpMap.put("profilePhotoUrl", dp.getProfilePhotoUrl());
            dpMap.put("profilePhotoStatus", dp.getProfilePhotoStatus().name());
            dpMap.put("profilePhotoRemarks", dp.getProfilePhotoRemarks());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deliveryPerson", dpMap);
            List<DeliveryPersonDocument> documents = deliveryPersonService.getDeliveryPersonDocuments(id);
            response.put("documents", documents);
            response.put("allDocumentsApproved", deliveryPersonService.areAllDocumentsApproved(id));
            response.put("onboardingStatus",
                    deliveryOnboardingService.buildOnboardingStatus(dp, documents));

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
     * Update profile details (name, profile photo URL) by delivery person ID.
     * Admin / fallback alternative to PUT /api/delivery-app/profile (JWT-based).
     */
    @PutMapping("/{id}/profile")
    public ResponseEntity<?> updateProfileById(
            @PathVariable Long id,
            @RequestBody Map<String, String> profileData) {
        try {
            String firstName = profileData.get("firstName");
            String lastName = profileData.get("lastName");
            String profilePhotoUrl = profileData.get("profilePhotoUrl");

            DeliveryPerson updated = deliveryPersonService.updateProfile(id, firstName, lastName, profilePhotoUrl);

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
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", e.getMessage(), "locked", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update profile: " + e.getMessage()));
        }
    }
}
