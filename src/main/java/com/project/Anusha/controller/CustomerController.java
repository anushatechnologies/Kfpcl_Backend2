package com.project.Anusha.controller;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.project.Anusha.dto.AuthResponse;
import com.project.Anusha.dto.RefreshTokenRequest;
import com.project.Anusha.dto.SignupRequest;
import com.project.Anusha.dto.TokenRefreshResponse;
import com.project.Anusha.dto.VerifyPhoneRequest;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.RefreshToken;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.security.JwtUtils;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.FirebaseService;
import com.project.Anusha.service.RefreshTokenService;
import com.project.Anusha.service.UserMainService;
import com.project.Anusha.service.UserLogService;
import com.project.Anusha.service.WalletService;
import com.project.Anusha.service.ReferralService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Authentication endpoints for the CustomerApp (Zepto).
 *
 * All endpoints are public (no JWT required) – see SecurityConfig.
 *
 * Flow:
 * New user: POST /api/auth/app/signup { firebaseIdToken, name }
 * Existing user: POST /api/auth/app/login { firebaseIdToken }
 * Pre-check: GET /api/auth/app/check-phone/{phone}
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class CustomerController {

    private final FirebaseService firebaseService;
    private final CustomerService customerService;
    private final UserMainService userMainService;
    private final UserLogService userLogService;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final WalletService walletService;
    private final ReferralService referralService;

    public CustomerController(FirebaseService firebaseService,
            CustomerService customerService,
            UserMainService userMainService,
            UserLogService userLogService,
            JwtUtils jwtUtils,
            RefreshTokenService refreshTokenService,
            WalletService walletService,
            ReferralService referralService) {
        this.firebaseService = firebaseService;
        this.customerService = customerService;
        this.userMainService = userMainService;
        this.userLogService = userLogService;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.walletService = walletService;
        this.referralService = referralService;
    }

    // ------------------------------------------------------------------ PRE-CHECK

    /**
     * Quick check whether a phone number is already registered as a Customer.
     * Called by the app BEFORE triggering Firebase OTP, so it can show
     * the correct screen (login vs sign-up).
     */
    @GetMapping("/check-phone/{phoneNumber}")
    public ResponseEntity<?> checkPhoneExists(@PathVariable String phoneNumber) {
        boolean exists = customerService.findByPhoneNumber(phoneNumber) != null;
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    // ------------------------------------------------------------------ NEW USER
    // SIGNUP

    /**
     * Register a brand-new customer.
     *
     * Steps performed in the backend:
     * 1. Verify Firebase ID token (phone already verified on device).
     * 2. Find-or-create a UserMain row (fid + phone).
     * 3. Check if a Customer row already exists → conflict.
     * 4. Add CUSTOMER role to UserMain.
     * 5. Create Customer row with the supplied username.
     * 6. Return JWT.
     */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        String displayName = "Customer";
        if (request.getName() != null && !request.getName().isBlank()) {
            displayName = request.getName().trim();
        }

        try {
            FirebaseToken token = firebaseService.verifyToken(request.getFirebaseIdToken());
            String phoneNumber = (String) token.getClaims().get("phone_number");
            String fid = token.getUid();

            // Get or create users_main row
            UserMain userMain = userMainService.findOrCreate(fid, phoneNumber);

            // Check if already registered as customer
            Optional<Customer> existing = customerService.findByUserMain(userMain);
            if (existing.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Account already exists. Please login."));
            }

            // Add CUSTOMER role (idempotent)
            userMainService.addRole(userMain, "CUSTOMER");

            // Update FCM token if provided
            if (request.getFcmToken() != null && !request.getFcmToken().isBlank()) {
                userMainService.updateFcmToken(phoneNumber, request.getFcmToken());
            }

            // Create customer profile
            Customer customer = customerService.createCustomer(userMain, displayName);
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                customer.setEmail(request.getEmail().trim());
                customerService.updateCustomer(customer);
            }

            // Assign a unique referral code so the new customer can immediately share theirs.
            referralService.ensureCodeFor(customer);

            // If a referral code was supplied, run fraud checks + issue scratch cards.
            if (request.getReferralCode() != null && !request.getReferralCode().isBlank()) {
                String ip = clientIp(httpRequest);
                referralService.handleSignup(customer, request.getReferralCode(), request.getDeviceId(), ip);
            } else if (request.getDeviceId() != null) {
                // Still record device/ip on customer for auditing even without a referral code.
                customer.setSignupDeviceId(request.getDeviceId());
                customer.setSignupIp(clientIp(httpRequest));
                customerService.updateCustomer(customer);
            }

            // Log signup action
            userLogService.log(userMain.getId(), "CUSTOMER", "SIGNUP", "User signed up with phone: " + phoneNumber,
                    null);

            RefreshTokenService.TokenPair tokenPair =
                    refreshTokenService.issueForUserMain(userMain, RefreshToken.ClientType.CUSTOMER_APP);
            return ResponseEntity.ok(buildAuthResponse(customer, tokenPair));

        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired Firebase token"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Signup failed: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ EXISTING
    // USER LOGIN

    /**
     * Login for an existing customer.
     *
     * Steps:
     * 1. Verify Firebase token.
     * 2. Look up Customer by phone number (via UserMain).
     * 3. If not found → 404 "Please sign up".
     * 4. Return JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody VerifyPhoneRequest request) {
        try {
            FirebaseToken token = firebaseService.verifyToken(request.getFirebaseIdToken());
            String phoneNumber = (String) token.getClaims().get("phone_number");
            String fid = token.getUid();

            Customer customer = customerService.findByPhoneNumber(phoneNumber);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No account found. Please sign up as a new user."));
            }

            // Update FCM token if provided
            if (request.getFcmToken() != null && !request.getFcmToken().isBlank()) {
                userMainService.updateFcmToken(phoneNumber, request.getFcmToken());
            }

            // Sync fid in UserMain if it ever changes (device re-install etc.)
            UserMain userMain = customer.getUserMain();
            if (!fid.equals(userMain.getFid())) {
                userMain.setFid(fid);
                userMainService.findOrCreate(fid, phoneNumber); // re-saves
            }

            // Log login action
            userLogService.log(userMain.getId(), "CUSTOMER", "LOGIN", "User logged in with phone: " + phoneNumber,
                    null);

            RefreshTokenService.TokenPair tokenPair =
                    refreshTokenService.issueForUserMain(userMain, RefreshToken.ClientType.CUSTOMER_APP);
            return ResponseEntity.ok(buildAuthResponse(customer, tokenPair));

        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired Firebase token"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ helper

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            RefreshTokenService.TokenPair tokenPair =
                    refreshTokenService.rotate(request.getRefreshToken(), RefreshToken.ClientType.CUSTOMER_APP);
            return ResponseEntity.ok(buildRefreshResponse(tokenPair));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.getRefreshToken(), RefreshToken.ClientType.CUSTOMER_APP);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    private AuthResponse buildAuthResponse(Customer customer, RefreshTokenService.TokenPair tokenPair) {
        AuthResponse response = new AuthResponse();
        response.setJwtToken(tokenPair.accessToken());
        response.setAccessToken(tokenPair.accessToken());
        response.setRefreshToken(tokenPair.refreshToken());
        response.setExpiresIn(tokenPair.expiresInSeconds());
        response.setCustomerId(customer.getId());
        response.setPhoneNumber(customer.getPhoneNumber()); // delegate to UserMain
        response.setName(customer.getUsername());
        response.setEmail(customer.getEmail());
        if (customer.getUserMain() != null) {
            response.setWalletBalance(walletService.getBalance(customer.getUserMain().getId()));
        }
        response.setRoles(customer.getUserMain() != null ? customer.getUserMain().getRoles() : "CUSTOMER");
        response.setReferralCode(customer.getReferralCode());
        return response;
    }

    /** Best-effort client IP, accounting for reverse proxies. */
    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
