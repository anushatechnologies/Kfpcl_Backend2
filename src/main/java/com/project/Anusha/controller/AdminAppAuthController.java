package com.project.Anusha.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.google.firebase.auth.FirebaseToken;
import com.project.Anusha.dto.AdminCodeVerifyRequest;
import com.project.Anusha.dto.LoginRequest;
import com.project.Anusha.dto.LoginResponse;
import com.project.Anusha.dto.RefreshTokenRequest;
import com.project.Anusha.dto.TokenRefreshResponse;
import com.project.Anusha.model.RefreshToken;
import com.project.Anusha.model.User;
import com.project.Anusha.service.FirebaseService;
import com.project.Anusha.service.RefreshTokenService;
import com.project.Anusha.service.UserService;

/**
 * Authentication endpoints for the native AdminApp.
 *
 * Tokens issued here are tagged with ClientType.ADMIN_APP, which lets the
 * same admin user remain logged-in on both the AdminPanel (web) and the
 * AdminApp (mobile) at the same time without one session evicting the
 * other. Within a single client type, the most recent login still
 * supersedes the previous session for that client.
 */
@RestController
@RequestMapping("/api/auth/adminapp")
public class AdminAppAuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private FirebaseService firebaseService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            User user = userService.getValidatedAdminByEmail(loginRequest.getEmail());

            return ResponseEntity.ok(buildLoginResponse(user));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Bad credentials"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/firebase-login")
    public ResponseEntity<?> firebaseLogin(@RequestBody Map<String, String> request) {
        String idToken = request.get("idToken");
        if (idToken == null || idToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "idToken is required"));
        }

        try {
            FirebaseToken firebaseToken = firebaseService.verifyToken(idToken);
            String email = firebaseToken.getEmail();

            if (email == null || email.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Firebase token did not include an email"));
            }

            User user = userService.getValidatedAdminByEmail(email);
            return ResponseEntity.ok(buildLoginResponse(user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/verify-admin-code")
    public ResponseEntity<?> verifyAdminCode(@RequestBody AdminCodeVerifyRequest request) {
        if (request.getChallengeToken() == null || request.getChallengeToken().isBlank()
                || request.getCode() == null || request.getCode().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "challengeToken and code are required"));
        }

        try {
            User user = userService.verifyAdminAccessCode(request.getChallengeToken(), request.getCode());
            return ResponseEntity.ok(buildSuccessfulLoginResponse(user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            RefreshTokenService.TokenPair tokenPair =
                    refreshTokenService.rotate(request.getRefreshToken(), RefreshToken.ClientType.ADMIN_APP);
            return ResponseEntity.ok(new TokenRefreshResponse(
                    tokenPair.accessToken(),
                    tokenPair.refreshToken(),
                    tokenPair.expiresInSeconds(),
                    tokenPair.accessToken(),
                    tokenPair.accessToken()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.getRefreshToken(), RefreshToken.ClientType.ADMIN_APP);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    private LoginResponse buildLoginResponse(User user) {
        if (userService.requiresAdminAccessCode(user)) {
            String challengeToken = userService.initiateAdminAccessChallenge(user);
            LoginResponse response = new LoginResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getRole(),
                    user.getName(),
                    user.isMustChangePassword(),
                    challengeToken,
                    userService.getEffectivePermissions(user)
            );
            attachAdminAccessInfo(response, user);
            return response;
        }

        User completedUser = userService.finalizeSuccessfulLogin(user);
        return buildSuccessfulLoginResponse(completedUser);
    }

    private LoginResponse buildSuccessfulLoginResponse(User user) {
        RefreshTokenService.TokenPair tokenPair =
                refreshTokenService.issueForAdmin(user, RefreshToken.ClientType.ADMIN_APP);

        LoginResponse response = new LoginResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.expiresInSeconds(),
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getName(),
                user.isMustChangePassword(),
                userService.getEffectivePermissions(user)
        );
        attachAdminAccessInfo(response, user);
        return response;
    }

    private void attachAdminAccessInfo(LoginResponse response, User user) {
        response.setAdminAccessExpiresAt(user.getAdminAccessExpiresAt());
        response.setAdminAccessRemainingSeconds(userService.getAdminAccessRemainingSeconds(user));
        response.setAdminAccessExpired(user.isAdminAccessExpired());
    }
}
