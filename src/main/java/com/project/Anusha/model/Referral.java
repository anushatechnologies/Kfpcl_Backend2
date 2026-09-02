package com.project.Anusha.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One row per referral relationship: who referred whom, captured at signup time.
 * Identity is bound to UserMain (mobile-verified), never to device.
 * Device/IP fields are anti-fraud signals only.
 */
@Entity
@Table(name = "referrals",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"referrer_user_main_id", "referee_user_main_id"})
        },
        indexes = {
                @Index(name = "idx_ref_referrer", columnList = "referrer_user_main_id"),
                @Index(name = "idx_ref_referee", columnList = "referee_user_main_id"),
                @Index(name = "idx_ref_status", columnList = "status"),
                @Index(name = "idx_ref_device", columnList = "signup_device_id")
        })
public class Referral {

    public enum Status {
        PENDING,    // created, fraud not yet evaluated
        QUALIFIED,  // passed fraud checks, scratch cards issued
        BLOCKED,    // failed fraud checks, no cards
        REWARDED    // both cards scratched + wallet credited
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_user_main_id", nullable = false)
    private UserMain referrer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referee_user_main_id", nullable = false)
    private UserMain referee;

    @Column(name = "referral_code", nullable = false, length = 16)
    private String referralCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "signup_device_id", length = 128)
    private String signupDeviceId;

    @Column(name = "signup_ip", length = 64)
    private String signupIp;

    @Column(name = "fraud_score")
    private Integer fraudScore = 0;

    @Column(name = "fraud_reasons", length = 500)
    private String fraudReasons;

    @Column(name = "qualified_at")
    private LocalDateTime qualifiedAt;

    @Column(name = "rewarded_at")
    private LocalDateTime rewardedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── getters / setters ─────────────────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserMain getReferrer() { return referrer; }
    public void setReferrer(UserMain referrer) { this.referrer = referrer; }
    public UserMain getReferee() { return referee; }
    public void setReferee(UserMain referee) { this.referee = referee; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getSignupDeviceId() { return signupDeviceId; }
    public void setSignupDeviceId(String signupDeviceId) { this.signupDeviceId = signupDeviceId; }
    public String getSignupIp() { return signupIp; }
    public void setSignupIp(String signupIp) { this.signupIp = signupIp; }
    public Integer getFraudScore() { return fraudScore; }
    public void setFraudScore(Integer fraudScore) { this.fraudScore = fraudScore; }
    public String getFraudReasons() { return fraudReasons; }
    public void setFraudReasons(String fraudReasons) { this.fraudReasons = fraudReasons; }
    public LocalDateTime getQualifiedAt() { return qualifiedAt; }
    public void setQualifiedAt(LocalDateTime qualifiedAt) { this.qualifiedAt = qualifiedAt; }
    public LocalDateTime getRewardedAt() { return rewardedAt; }
    public void setRewardedAt(LocalDateTime rewardedAt) { this.rewardedAt = rewardedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
