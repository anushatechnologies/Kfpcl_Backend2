package com.project.Anusha.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Expo push token for the AdminApp (React Native / Expo).
 *
 * One row per physical device — uniqueness is on `expoPushToken` because
 * Google/Apple keep the same token for the lifetime of an app install,
 * and a token always belongs to exactly one device.
 *
 * When admin A logs out and admin B logs in on the same device, the
 * `admin` foreign key is overwritten via upsert. That is intentional —
 * the device is now admin B's, so pushes for that token should go to B.
 *
 * `active = false` when the device unregisters (logout / account delete)
 * or when Expo returns `DeviceNotRegistered` for that token.
 */
@Entity
@Table(name = "admin_push_tokens", indexes = {
        @Index(name = "idx_admin_push_token_admin", columnList = "admin_id"),
        @Index(name = "idx_admin_push_token_active", columnList = "active, app_type")
})
public class AdminPushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @Column(name = "expo_push_token", nullable = false, unique = true, length = 255)
    private String expoPushToken;

    /** "android" or "ios" — lowercased before save. */
    @Column(nullable = false, length = 20)
    private String platform;

    /** "ADMIN_APP" — kept as a string so we can extend (e.g. ADMIN_PANEL_WEB) later without a migration. */
    @Column(name = "app_type", nullable = false, length = 30)
    private String appType;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getAdmin() { return admin; }
    public void setAdmin(User admin) { this.admin = admin; }

    public String getExpoPushToken() { return expoPushToken; }
    public void setExpoPushToken(String expoPushToken) { this.expoPushToken = expoPushToken; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getAppType() { return appType; }
    public void setAppType(String appType) { this.appType = appType; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
