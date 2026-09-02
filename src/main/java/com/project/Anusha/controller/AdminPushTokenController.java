package com.project.Anusha.controller;

import com.project.Anusha.dto.SavePushTokenRequest;
import com.project.Anusha.service.AdminPushTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin App push-token registration.
 *
 * The native AdminApp (Expo) calls these endpoints after login / on logout.
 * Endpoint path matches the app env var `EXPO_PUBLIC_PUSH_TOKEN_SYNC_PATH`.
 */
@RestController
@RequestMapping("/api/admin/notifications")
public class AdminPushTokenController {

    private final AdminPushTokenService service;

    public AdminPushTokenController(AdminPushTokenService service) {
        this.service = service;
    }

    /** Called after the admin signs in on the device — registers/updates the Expo token. */
    @PostMapping("/push-token")
    public ResponseEntity<?> saveToken(@Valid @RequestBody SavePushTokenRequest req,
                                       Authentication authentication) {
        service.upsert(authentication, req);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Called on logout or account delete. Idempotent — silently no-ops on an
     * unknown token.
     *
     * Body: { "expoPushToken": "ExponentPushToken[...]" }
     */
    @DeleteMapping("/push-token")
    public ResponseEntity<?> removeToken(@RequestBody Map<String, String> body) {
        service.deactivate(body == null ? null : body.get("expoPushToken"));
        return ResponseEntity.ok(Map.of("success", true));
    }
}
