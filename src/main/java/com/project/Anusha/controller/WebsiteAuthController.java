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
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.FirebaseService;
import com.project.Anusha.service.RefreshTokenService;
import com.project.Anusha.service.ReferralService;
import com.project.Anusha.service.UserLogService;
import com.project.Anusha.service.UserMainService;
import com.project.Anusha.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Authentication endpoints for the customer-facing Website.
 *
 * Tokens issued here are tagged with ClientType.WEBSITE, which lets the
 * same customer remain logged-in on both the mobile CustomerApp and the
 * Website at the same time without one session evicting the other.
 */
@RestController
@RequestMapping("/api/auth/website")
@CrossOrigin(origins = "*")
public class WebsiteAuthController {

    private final FirebaseService firebaseService;
    private final CustomerService customerService;
    private final UserMainService userMainService;
    private final UserLogService userLogService;
    private final RefreshTokenService refreshTokenService;
    private final WalletService walletService;
    private final ReferralService referralService;

    public WebsiteAuthController(FirebaseService firebaseService,
                                 CustomerService customerService,
                                 UserMainService userMainService,
                                 UserLogService userLogService,
                                 RefreshTokenService refreshTokenService,
                                 WalletService walletService,
                                 ReferralService referralService) {
        this.firebaseService = firebaseService;
        this.customerService = customerService;
        this.userMainService = userMainService;
        this.userLogService = userLogService;
        this.refreshTokenService = refreshTokenService;
        this.walletService = walletService;
        this.referralService = referralService;
    }

    @GetMapping("/check-phone/{phoneNumber}")
    public ResponseEntity<?> checkPhoneExists(@PathVariable String phoneNumber) {
        boolean exists = customerService.findByPhoneNumber(phoneNumber) != null;
        return ResponseEntity.ok(Map.of("exists", exists));
    }

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

            UserMain userMain = userMainService.findOrCreate(fid, phoneNumber);

            Optional<Customer> existing = customerService.findByUserMain(userMain);
            if (existing.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Account already exists. Please login."));
            }

            userMainService.addRole(userMain, "CUSTOMER");

            if (request.getFcmToken() != null && !request.getFcmToken().isBlank()) {
                userMainService.updateFcmToken(phoneNumber, request.getFcmToken());
            }

            Customer customer = customerService.createCustomer(userMain, displayName);
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                customer.setEmail(request.getEmail().trim());
                customerService.updateCustomer(customer);
            }

            referralService.ensureCodeFor(customer);

            if (request.getReferralCode() != null && !request.getReferralCode().isBlank()) {
                String ip = clientIp(httpRequest);
                referralService.handleSignup(customer, request.getReferralCode(), request.getDeviceId(), ip);
            } else if (request.getDeviceId() != null) {
                customer.setSignupDeviceId(request.getDeviceId());
                customer.setSignupIp(clientIp(httpRequest));
                customerService.updateCustomer(customer);
            }

            userLogService.log(userMain.getId(), "CUSTOMER", "SIGNUP_WEBSITE",
                    "User signed up via website with phone: " + phoneNumber, null);

            RefreshTokenService.TokenPair tokenPair =
                    refreshTokenService.issueForUserMain(userMain, RefreshToken.ClientType.WEBSITE);
            return ResponseEntity.ok(buildAuthResponse(customer, tokenPair));

        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired Firebase token"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Signup failed: " + e.getMessage()));
        }
    }

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

            if (request.getFcmToken() != null && !request.getFcmToken().isBlank()) {
                userMainService.updateFcmToken(phoneNumber, request.getFcmToken());
            }

            UserMain userMain = customer.getUserMain();
            if (!fid.equals(userMain.getFid())) {
                userMain.setFid(fid);
                userMainService.findOrCreate(fid, phoneNumber);
            }

            userLogService.log(userMain.getId(), "CUSTOMER", "LOGIN_WEBSITE",
                    "User logged in via website with phone: " + phoneNumber, null);

            RefreshTokenService.TokenPair tokenPair =
                    refreshTokenService.issueForUserMain(userMain, RefreshToken.ClientType.WEBSITE);
            return ResponseEntity.ok(buildAuthResponse(customer, tokenPair));

        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired Firebase token"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            RefreshTokenService.TokenPair tokenPair =
                    refreshTokenService.rotate(request.getRefreshToken(), RefreshToken.ClientType.WEBSITE);
            return ResponseEntity.ok(buildRefreshResponse(tokenPair));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.getRefreshToken(), RefreshToken.ClientType.WEBSITE);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    private AuthResponse buildAuthResponse(Customer customer, RefreshTokenService.TokenPair tokenPair) {
        AuthResponse response = new AuthResponse();
        response.setJwtToken(tokenPair.accessToken());
        response.setAccessToken(tokenPair.accessToken());
        response.setRefreshToken(tokenPair.refreshToken());
        response.setExpiresIn(tokenPair.expiresInSeconds());
        response.setCustomerId(customer.getId());
        response.setPhoneNumber(customer.getPhoneNumber());
        response.setName(customer.getUsername());
        response.setEmail(customer.getEmail());
        if (customer.getUserMain() != null) {
            response.setWalletBalance(walletService.getBalance(customer.getUserMain().getId()));
        }
        response.setRoles(customer.getUserMain() != null ? customer.getUserMain().getRoles() : "CUSTOMER");
        response.setReferralCode(customer.getReferralCode());
        return response;
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

    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
