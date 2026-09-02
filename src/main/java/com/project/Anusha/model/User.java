package com.project.Anusha.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String name;

    private String otp;
    private LocalDateTime otpExpiry;

    private boolean enabled = true;

    // true = admin must change password on next login (set by super admin)
    private boolean mustChangePassword = false;

    // which super admin created this admin (null for super admin itself)
    private Long createdById;

    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    private String resetToken;
    private LocalDateTime resetTokenExpiry;

    private String adminAccessCodeHash;
    private LocalDateTime adminAccessCodeUpdatedAt;

    private String adminAccessChallengeToken;
    private LocalDateTime adminAccessChallengeExpiry;

    @Column(name = "admin_access_expires_at")
    private LocalDateTime adminAccessExpiresAt;

    // ROLE_ADMIN or ROLE_SUPER_ADMIN
    private String role = "ROLE_ADMIN";

    @Column(name = "permission_keys", length = 2000)
    private String permissionKeys = "";

    public User() {}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getOtp() { return otp; }
	public void setOtp(String otp) { this.otp = otp; }

	public LocalDateTime getOtpExpiry() { return otpExpiry; }
	public void setOtpExpiry(LocalDateTime otpExpiry) { this.otpExpiry = otpExpiry; }

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }

	public boolean isMustChangePassword() { return mustChangePassword; }
	public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

	public Long getCreatedById() { return createdById; }
	public void setCreatedById(Long createdById) { this.createdById = createdById; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public LocalDateTime getLastLoginAt() { return lastLoginAt; }
	public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

	public String getResetToken() { return resetToken; }
	public void setResetToken(String resetToken) { this.resetToken = resetToken; }

	public LocalDateTime getResetTokenExpiry() { return resetTokenExpiry; }
	public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) { this.resetTokenExpiry = resetTokenExpiry; }

	public String getAdminAccessCodeHash() { return adminAccessCodeHash; }
	public void setAdminAccessCodeHash(String adminAccessCodeHash) { this.adminAccessCodeHash = adminAccessCodeHash; }

	public LocalDateTime getAdminAccessCodeUpdatedAt() { return adminAccessCodeUpdatedAt; }
	public void setAdminAccessCodeUpdatedAt(LocalDateTime adminAccessCodeUpdatedAt) { this.adminAccessCodeUpdatedAt = adminAccessCodeUpdatedAt; }

	public String getAdminAccessChallengeToken() { return adminAccessChallengeToken; }
	public void setAdminAccessChallengeToken(String adminAccessChallengeToken) { this.adminAccessChallengeToken = adminAccessChallengeToken; }

	public LocalDateTime getAdminAccessChallengeExpiry() { return adminAccessChallengeExpiry; }
	public void setAdminAccessChallengeExpiry(LocalDateTime adminAccessChallengeExpiry) { this.adminAccessChallengeExpiry = adminAccessChallengeExpiry; }

	public LocalDateTime getAdminAccessExpiresAt() { return adminAccessExpiresAt; }
	public void setAdminAccessExpiresAt(LocalDateTime adminAccessExpiresAt) { this.adminAccessExpiresAt = adminAccessExpiresAt; }

	public String getRole() { return role; }
	public void setRole(String role) { this.role = role; }

	public String getPermissionKeys() { return permissionKeys; }
	public void setPermissionKeys(String permissionKeys) { this.permissionKeys = permissionKeys; }

    public boolean hasAdminAccessCode() {
        return adminAccessCodeHash != null && !adminAccessCodeHash.isBlank();
    }

    public boolean isAdminAccessExpired() {
        return isAdminAccessExpiredAt(LocalDateTime.now());
    }

    public boolean isAdminAccessExpiredAt(LocalDateTime now) {
        return "ROLE_ADMIN".equals(role)
                && adminAccessExpiresAt != null
                && !adminAccessExpiresAt.isAfter(now);
    }
}
