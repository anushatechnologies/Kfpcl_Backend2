package com.project.Anusha.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Common bridge table that links a Firebase-verified phone number & UID
 * to both the Customer and DeliveryPerson profiles.
 *
 * One row per unique phone number / Firebase UID.
 * A user who registers in BOTH apps will have roles = "CUSTOMER,DELIVERY_PERSON".
 */
@Entity
@Table(name = "users_main",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "fid"),
                @UniqueConstraint(columnNames = "phone_number")
        })
public class UserMain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Firebase UID – unique identifier assigned by Firebase after phone OTP verification */
    @Column(name = "fid", nullable = false, unique = true, length = 128)
    private String fid;

    /** Mobile number as verified by Firebase (e.g. "+919948598350") */
    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    /**
     * Comma-separated roles: "CUSTOMER", "DELIVERY_PERSON", or "CUSTOMER,DELIVERY_PERSON"
     * Never null – defaults to empty string until first role is assigned.
     */
    @Column(name = "roles", length = 150, nullable = false)
    private String roles = "";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "wallet_balance", precision = 10, scale = 2)
    private java.math.BigDecimal walletBalance = java.math.BigDecimal.ZERO;

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @Version
    private Long version;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserFcmToken> fcmTokens = new ArrayList<>();

    // ------------------------------------------------------------------ constructors

    public UserMain() {}

    public UserMain(String fid, String phoneNumber) {
        this.fid = fid;
        this.phoneNumber = phoneNumber;
    }

    // ------------------------------------------------------------------ role helpers

    /** Returns all current roles as a Set. */
    public Set<String> getRoleSet() {
        if (roles == null || roles.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(roles.split(",")));
    }

    /** Adds a role (idempotent). Saves nothing – caller must persist this entity. */
    public void addRole(String role) {
        Set<String> set = getRoleSet();
        set.add(role.trim().toUpperCase());
        this.roles = String.join(",", set);
    }

    public boolean hasRole(String role) {
        return getRoleSet().contains(role.trim().toUpperCase());
    }

    // ------------------------------------------------------------------ getters / setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFid() { return fid; }
    public void setFid(String fid) { this.fid = fid; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public java.math.BigDecimal getWalletBalance() { return walletBalance; }
    public void setWalletBalance(java.math.BigDecimal walletBalance) { this.walletBalance = walletBalance; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public List<UserFcmToken> getFcmTokens() { return fcmTokens; }
    public void setFcmTokens(List<UserFcmToken> fcmTokens) { this.fcmTokens = fcmTokens; }
}
