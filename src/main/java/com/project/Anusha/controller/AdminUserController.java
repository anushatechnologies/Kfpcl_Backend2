package com.project.Anusha.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.project.Anusha.dto.AdminAccessTimeRequest;
import com.project.Anusha.dto.AdminUserRequest;
import com.project.Anusha.dto.AdminUserResponse;
import com.project.Anusha.dto.AdminAccessCodeRequest;
import com.project.Anusha.dto.AdminPermissionsRequest;
import com.project.Anusha.model.User;
import com.project.Anusha.service.UserService;

/**
 * Admin user management — accessible only by ROLE_SUPER_ADMIN.
 * Security is enforced in SecurityConfig (.hasRole("SUPER_ADMIN")).
 */
@RestController
@RequestMapping("/api/super-admin/admin-users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    /** List all admin + super-admin accounts */
    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> listAdmins() {
        List<AdminUserResponse> result = userService.getAllAdminUsers()
                .stream()
                .map(AdminUserResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** Create a new ROLE_ADMIN account (super admin only) */
    @PostMapping
    public ResponseEntity<?> createAdmin(@RequestBody AdminUserRequest req,
                                         Authentication auth) {
        try {
            User superAdmin = userService.findByEmail(auth.getName());
            User created = userService.createAdminUser(req.getEmail(), req.getName(), superAdmin.getId());
            return ResponseEntity.ok(AdminUserResponse.from(created));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Toggle enable/disable for an admin account */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> toggleStatus(@PathVariable Long id, Authentication auth) {
        try {
            User superAdmin = userService.findByEmail(auth.getName());
            User updated = userService.toggleAdminStatus(id, superAdmin.getId());
            return ResponseEntity.ok(AdminUserResponse.from(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Reset admin password and send a new setup/reset email. */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id, Authentication auth) {
        try {
            User superAdmin = userService.findByEmail(auth.getName());
            userService.resetAdminPassword(id, superAdmin.getId());
            return ResponseEntity.ok(Map.of("message", "Password reset email sent successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Set or update a 6-digit admin access code. */
    @PostMapping("/{id}/access-code")
    public ResponseEntity<?> setAccessCode(@PathVariable Long id,
                                           @RequestBody AdminAccessCodeRequest req,
                                           Authentication auth) {
        try {
            User superAdmin = userService.findByEmail(auth.getName());
            User updated = userService.setAdminAccessCode(id, req.getCode(), superAdmin.getId());
            return ResponseEntity.ok(AdminUserResponse.from(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Assign the admin-panel components/modules this admin can access. */
    @PutMapping("/{id}/permissions")
    public ResponseEntity<?> updatePermissions(@PathVariable Long id,
                                               @RequestBody AdminPermissionsRequest req,
                                               Authentication auth) {
        try {
            User superAdmin = userService.findByEmail(auth.getName());
            User updated = userService.updateAdminPermissions(id, req.getPermissions(), superAdmin.getId());
            return ResponseEntity.ok(AdminUserResponse.from(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Set how long a normal admin can access the admin panel from now. */
    @PutMapping("/{id}/access-time")
    public ResponseEntity<?> updateAccessTime(@PathVariable Long id,
                                              @RequestBody AdminAccessTimeRequest req,
                                              Authentication auth) {
        try {
            User superAdmin = userService.findByEmail(auth.getName());
            User updated = userService.updateAdminAccessTime(id, req, superAdmin.getId());
            return ResponseEntity.ok(AdminUserResponse.from(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
