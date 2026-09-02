package com.project.Anusha.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Customer profile – specific to the CustomerApp (Zepto).
 * All Firebase / phone / role data is stored in {@link UserMain}.
 * This table only holds customer-specific fields.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the common user record that holds fid, phoneNumber, and roles.
     * OneToOne because one UserMain entry can have AT MOST one customer profile.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_main_id", nullable = false, unique = true)
    private UserMain userMain;

    /**
     * Display name chosen by the customer during signup.
     * This is the only required field collected in the CustomerApp.
     */
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /** Optional e-mail address */
    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Unique 6-char code shown to this customer for them to share with friends. */
    @Column(name = "referral_code", unique = true, length = 16)
    private String referralCode;

    /** Code that was used at signup (the friend's code). Null if user signed up directly. */
    @Column(name = "referred_by_code", length = 16)
    private String referredByCode;

    /** Device fingerprint captured at signup, used for fraud checks only. */
    @Column(name = "signup_device_id", length = 128)
    private String signupDeviceId;

    /** IP captured at signup, used for fraud checks only. */
    @Column(name = "signup_ip", length = 64)
    private String signupIp;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ------------------------------------------------------------------ constructors

    public Customer() {}

    // Convenience constructor
    public Customer(UserMain userMain, String username) {
        this.userMain = userMain;
        this.username = username;
    }

    // ------------------------------------------------------------------ convenience delegators

    /** Shortcut to get phone number from UserMain */
    public String getPhoneNumber() {
        return userMain != null ? userMain.getPhoneNumber() : null;
    }

    /** Shortcut to get Firebase UID from UserMain */
    public String getFirebaseUid() {
        return userMain != null ? userMain.getFid() : null;
    }

    /** Shortcut to get role string from UserMain */
    public String getRole() {
        return userMain != null ? userMain.getRoles() : null;
    }

    /** Compatibility alias – returns the username as the "first" name. */
    public String getFirstName() { return username; }

    /** Compatibility alias – returns empty string (no separate last name stored). */
    public String getLastName() { return ""; }

    /** Compatibility alias for phone number via UserMain. */
    public String getPhone() { return getPhoneNumber(); }

    // ------------------------------------------------------------------ getters / setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserMain getUserMain() { return userMain; }
    public void setUserMain(UserMain userMain) { this.userMain = userMain; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    /**
     * Backward-compatible alias: many parts of the codebase still call getName().
     * Returns the same value as getUsername().
     */
    public String getName() { return username; }
    public void setName(String name) { this.username = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

    public String getReferredByCode() { return referredByCode; }
    public void setReferredByCode(String referredByCode) { this.referredByCode = referredByCode; }

    public String getSignupDeviceId() { return signupDeviceId; }
    public void setSignupDeviceId(String signupDeviceId) { this.signupDeviceId = signupDeviceId; }

    public String getSignupIp() { return signupIp; }
    public void setSignupIp(String signupIp) { this.signupIp = signupIp; }
}