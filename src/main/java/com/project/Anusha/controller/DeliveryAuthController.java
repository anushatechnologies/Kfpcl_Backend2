package com.project.Anusha.controller;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.project.Anusha.dto.DeliverySignupRequest;
import com.project.Anusha.dto.RefreshTokenRequest;
import com.project.Anusha.dto.TokenRefreshResponse;
import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.RefreshToken;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.security.JwtUtils;
import com.project.Anusha.service.DeliveryPersonService;
import com.project.Anusha.service.DeliverySignupService;
import com.project.Anusha.service.DeliveryOnboardingService;
import com.project.Anusha.service.FareRuleService;
import com.project.Anusha.service.FirebaseService;
import com.project.Anusha.service.RefreshTokenService;
import com.project.Anusha.service.S3Service;
import com.project.Anusha.service.UserMainService;
import com.project.Anusha.service.UserLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

/**
 * Authentication endpoints for the DeliveryApp (ZeptoPartner).
 *
 * All endpoints under /api/delivery/auth/** are public (no JWT required).
 *
 * Flows:
 *   Pre-check: GET  /api/delivery/auth/check-phone/{phone}
 *   Register:  POST /api/delivery/auth/signup  { firebaseIdToken, firstName, lastName }
 *   Login:     POST /api/delivery/auth/login   { firebaseIdToken }
 *
 * Cross-app scenario:
 *   If a user already registered in CustomerApp with the same phone number,
 *   the Firebase OTP still works. The backend reuses the existing UserMain row
 *   and simply adds the DELIVERY_PERSON role + creates a new delivery_persons row.
 */
@RestController
@RequestMapping("/api/delivery/auth")
@CrossOrigin(origins = "*")
public class DeliveryAuthController {

    private final FirebaseService firebaseService;
    private final DeliveryPersonService deliveryPersonService;
    private final UserMainService userMainService;
    private final DeliverySignupService deliverySignupService;
    private final DeliveryOnboardingService deliveryOnboardingService;
    private final FareRuleService fareRuleService;
    private final UserLogService userLogService;
    private final S3Service s3Service;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    public DeliveryAuthController(FirebaseService firebaseService,
                                  DeliveryPersonService deliveryPersonService,
                                  UserMainService userMainService,
                                  DeliverySignupService deliverySignupService,
                                  DeliveryOnboardingService deliveryOnboardingService,
                                  FareRuleService fareRuleService,
                                  UserLogService userLogService,
                                  S3Service s3Service,
                                  JwtUtils jwtUtils,
                                  RefreshTokenService refreshTokenService) {
        this.firebaseService = firebaseService;
        this.deliveryPersonService = deliveryPersonService;
        this.userMainService = userMainService;
        this.deliverySignupService = deliverySignupService;
        this.deliveryOnboardingService = deliveryOnboardingService;
        this.fareRuleService = fareRuleService;
        this.userLogService = userLogService;
        this.s3Service = s3Service;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
    }

    // ------------------------------------------------------------------ PROFILE PHOTO UPLOAD

    /**
     * Upload a profile photo to S3 BEFORE signup (no deliveryPersonId needed yet).
     *
     * Flow the mobile app must follow:
     *   Step 1: POST /api/delivery/auth/upload-profile-photo  (multipart, file = photo)
     *           ← returns { photoUrl: "https://bucket.s3.region.amazonaws.com/delivery-boys/profile-photos/..." }
     *   Step 2: POST /api/delivery/auth/signup { ..., profilePhotoUrl: "<url from Step 1>" }
     *
     * This endpoint is public (under /api/delivery/auth/**) — no JWT required.
     * Max file size is controlled by spring.servlet.multipart.max-file-size (currently 20MB).
     */
    @PostMapping("/upload-profile-photo")
    public ResponseEntity<?> uploadProfilePhoto(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Photo file is required"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed (JPEG, PNG, WEBP)"));
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "File size must not exceed 5MB"));
        }

        try {
            String photoUrl = s3Service.uploadFile(file, "delivery-boys/profile-photos");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "photoUrl", photoUrl,
                    "message", "Profile photo uploaded. Pass 'photoUrl' as profilePhotoUrl in the signup request."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload profile photo: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ PRE-CHECK

    /**
     * Quick check before triggering OTP: is this phone already registered as delivery person?
     * Returns: { exists: boolean, isApproved: boolean }
     */
    @GetMapping("/check-phone/{phoneNumber}")
    public ResponseEntity<?> checkPhoneExists(@PathVariable String phoneNumber) {
        Optional<DeliveryPerson> dpOpt = deliveryPersonService.findByPhoneNumber(phoneNumber);
        if (dpOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("exists", false, "isApproved", false));
        }
        DeliveryPerson dp = dpOpt.get();
        return ResponseEntity.ok(Map.of(
                "exists", true,
                "isApproved", dp.isApprovedByAdmin(),
                "approvalStatus", dp.getApprovalStatus().name()
        ));
    }

    // ------------------------------------------------------------------ SIGNUP (new delivery person)

    /**
     * Register a new delivery person.
     *
     * Steps:
     *  1. Verify Firebase token (OTP already completed on device).
     *  2. Find-or-create UserMain (may already exist if they are also a Customer).
     *  3. Check if a DeliveryPerson row already exists → conflict.
     *  4. Add DELIVERY_PERSON role to UserMain.
     *  5. Create DeliveryPerson row with firstName + lastName.
     *  6. Return JWT (but person is NOT approved – must upload docs + await admin).
     */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody DeliverySignupRequest request) {
        // Basic validation
        if (request.getFirstName() == null || request.getFirstName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "First name is required"));
        }
        if (request.getLastName() == null || request.getLastName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Last name is required"));
        }

        try {
            FirebaseToken token = firebaseService.verifyToken(request.getFirebaseIdToken());
            String phoneNumber = (String) token.getClaims().get("phone_number");
            String fid = token.getUid();

            // Get or create users_main row (reuses existing if also a Customer)
            UserMain userMain = userMainService.findOrCreate(fid, phoneNumber);

            // Check if already registered as delivery person
            if (deliveryPersonService.existsByUserMain(userMain)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Phone number already registered as a delivery person. Please login."));
            }

            // Atomic signup: adds DELIVERY_PERSON role AND creates delivery_persons row
            // If the delivery_persons insert fails, the role update is rolled back too
            DeliveryPerson newPerson = deliverySignupService.signupDeliveryPerson(
                    userMain,
                    request.getFirstName(),
                    request.getLastName(),
                    request.getVehicleType(),
                    request.getVehicleModel(),
                    request.getRegistrationNumber(),
                    request.getProfilePhotoUrl());

            // Update FCM token if present in request
            if (request.getFcmToken() != null && !request.getFcmToken().isBlank()) {
                userMainService.updateFcmToken(phoneNumber, request.getFcmToken());
            }

            // Log signup action
            userLogService.log(userMain.getId(), "DELIVERY_PERSON", "SIGNUP", "Delivery person signed up with phone: " + phoneNumber, null);

            RefreshTokenService.TokenPair tokenPair =
                    refreshTokenService.issueForUserMain(userMain, RefreshToken.ClientType.DELIVERY_APP);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(buildAuthResponse(newPerson, tokenPair, "Registration successful. Please upload your documents and wait for admin approval."));

        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired Firebase token"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ LOGIN (existing delivery person)

    /**
     * Login for an existing, approved delivery person.
     *
     * Steps:
     *  1. Verify Firebase token.
     *  2. Find DeliveryPerson by phone (via UserMain).
     *  3. If not found → 404 "Please register".
     *  4. If not admin-approved → 403 "Pending approval".
     *  5. Update lastActiveTime and return JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String firebaseIdToken = request.get("firebaseIdToken");
        String fcmToken = request.get("fcmToken"); // New: optional fcmToken in login

        if (firebaseIdToken == null || firebaseIdToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Firebase token is required"));
        }

        try {
            FirebaseToken token = firebaseService.verifyToken(firebaseIdToken);
            String phoneNumber = (String) token.getClaims().get("phone_number");

            Optional<DeliveryPerson> dpOpt = deliveryPersonService.findByPhoneNumber(phoneNumber);
            if (dpOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No delivery account found. Please register first."));
            }

            DeliveryPerson person = dpOpt.get();

            // Handle FCM token update if provided
            if (fcmToken != null && !fcmToken.isBlank()) {
                userMainService.updateFcmToken(phoneNumber, fcmToken);
            }
            
            // Update last active time
            person.setLastActiveTime(java.time.LocalDateTime.now());
            deliveryPersonService.updateDeliveryPerson(person);

            // Log login action
            userLogService.log(person.getUserMain().getId(), "DELIVERY_PERSON", "LOGIN", "Delivery person logged in with phone: " + phoneNumber, null);

            RefreshTokenService.TokenPair tokenPair =
                    refreshTokenService.issueForUserMain(person.getUserMain(), RefreshToken.ClientType.DELIVERY_APP);
            
            String message = null;
            if (!person.isApprovedByAdmin()) {
                message = "Your account is pending admin approval. Please ensure all documents are uploaded.";
            }

            return ResponseEntity.ok(buildAuthResponse(person, tokenPair, message));

        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired Firebase token"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ PUBLIC FARE RULES

    /**
     * GET /api/delivery/auth/fare-rules
     * Public endpoint — partner app fetches all fare rules to display to the rider.
     * No JWT required. Returns same data as the admin fare-rules endpoint.
     */
    @GetMapping("/fare-rules")
    public ResponseEntity<?> getPublicFareRules() {
        try {
            java.util.List<com.project.Anusha.model.FareRule> rules = fareRuleService.getAllFareRules();
            return ResponseEntity.ok(Map.of("success", true, "fareRules", rules));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch fare rules: " + e.getMessage()));
        }
    }

    /**
     * POST /api/delivery/auth/fare/calculate
     * Public endpoint — partner app calculates fare before accepting an order.
     * Body: { "vehicleType": "BIKE", "distanceKm": 9.7 }
     */
    @PostMapping("/fare/calculate")
    public ResponseEntity<?> calculateFare(@RequestBody Map<String, Object> body) {
        try {
            String vehicleTypeStr = (String) body.get("vehicleType");
            Number distanceNum = (Number) body.get("distanceKm");
            if (vehicleTypeStr == null || distanceNum == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "vehicleType and distanceKm are required"));
            }
            DeliveryPerson.VehicleType vt = DeliveryPerson.VehicleType.valueOf(vehicleTypeStr.toUpperCase());
            Map<String, Object> breakdown = fareRuleService.calculateFareBreakdown(vt, distanceNum.doubleValue());
            return ResponseEntity.ok(Map.of("success", true, "fareBreakdown", breakdown));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Fare calculation failed: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ helper

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            RefreshTokenService.TokenPair tokenPair =
                    refreshTokenService.rotate(request.getRefreshToken(), RefreshToken.ClientType.DELIVERY_APP);
            return ResponseEntity.ok(buildRefreshResponse(tokenPair));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.getRefreshToken(), RefreshToken.ClientType.DELIVERY_APP);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    private Map<String, Object> buildAuthResponse(DeliveryPerson person, RefreshTokenService.TokenPair tokenPair, String message) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        java.util.List<com.project.Anusha.model.DeliveryPersonDocument> documents =
                deliveryPersonService.getDeliveryPersonDocuments(person.getId());
        body.put("jwtToken", tokenPair.accessToken());
        body.put("accessToken", tokenPair.accessToken());
        body.put("refreshToken", tokenPair.refreshToken());
        body.put("expiresIn", tokenPair.expiresInSeconds());
        body.put("deliveryPersonId", person.getId());
        body.put("phoneNumber", person.getPhoneNumber());
        body.put("firstName", person.getFirstName());
        body.put("lastName", person.getLastName());
        body.put("fullName", person.getFullName());
        body.put("isApprovedByAdmin", person.isApprovedByAdmin());
        body.put("approvalStatus", person.getApprovalStatus().name());
        body.put("isVerified", person.isVerified());
        // Profile photo
        body.put("profilePhotoUrl", person.getProfilePhotoUrl() != null ? person.getProfilePhotoUrl() : "");
        body.put("profilePhotoStatus", person.getProfilePhotoStatus().name());
        body.put("profilePhotoLocked", person.getProfilePhotoStatus() == com.project.Anusha.model.DeliveryPersonDocument.DocumentStatus.APPROVED);
        // Vehicle info
        body.put("vehicleType", person.getVehicleType().name());
        body.put("vehicleModel", person.getVehicleModel());
        body.put("registrationNumber", person.getRegistrationNumber());
        body.put("onboardingStatus", deliveryOnboardingService.buildOnboardingStatus(person, documents));
        if (message != null) body.put("message", message);
        return body;
    }

    private TokenRefreshResponse buildRefreshResponse(RefreshTokenService.TokenPair tokenPair) {
        return new TokenRefreshResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.expiresInSeconds(),
                tokenPair.accessToken(),
                tokenPair.accessToken()
        );
    }
}
