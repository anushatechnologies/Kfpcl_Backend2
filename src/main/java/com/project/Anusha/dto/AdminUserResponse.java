package com.project.Anusha.dto;

import com.project.Anusha.model.User;
import com.project.Anusha.util.AdminPermissions;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class AdminUserResponse {
    private Long id;
    private String email;
    private String name;
    private String role;
    private boolean enabled;
    private boolean mustChangePassword;
    private boolean hasAdminAccessCode;
    private Long createdById;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime adminAccessExpiresAt;
    private Long adminAccessRemainingSeconds;
    private boolean adminAccessExpired;
    private List<String> permissions;

    public static AdminUserResponse from(User u) {
        AdminUserResponse r = new AdminUserResponse();
        r.id = u.getId();
        r.email = u.getEmail();
        r.name = u.getName();
        r.role = u.getRole();
        r.enabled = u.isEnabled();
        r.mustChangePassword = u.isMustChangePassword();
        r.hasAdminAccessCode = u.hasAdminAccessCode();
        r.createdById = u.getCreatedById();
        r.createdAt = u.getCreatedAt();
        r.lastLoginAt = u.getLastLoginAt();
        r.adminAccessExpiresAt = u.getAdminAccessExpiresAt();
        r.adminAccessRemainingSeconds = u.getAdminAccessExpiresAt() == null
                ? null
                : Math.max(0, Duration.between(LocalDateTime.now(), u.getAdminAccessExpiresAt()).getSeconds());
        r.adminAccessExpired = u.isAdminAccessExpired();
        r.permissions = "ROLE_SUPER_ADMIN".equals(u.getRole())
                ? AdminPermissions.all()
                : AdminPermissions.parse(u.getPermissionKeys());
        return r;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public boolean isHasAdminAccessCode() { return hasAdminAccessCode; }
    public Long getCreatedById() { return createdById; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public LocalDateTime getAdminAccessExpiresAt() { return adminAccessExpiresAt; }
    public Long getAdminAccessRemainingSeconds() { return adminAccessRemainingSeconds; }
    public boolean isAdminAccessExpired() { return adminAccessExpired; }
    public List<String> getPermissions() { return permissions; }
}
