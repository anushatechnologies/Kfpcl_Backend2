package com.project.Anusha.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Singleton (id=1) row holding the active reward configuration.
 * Editable from admin panel without redeploy.
 *
 * All point values are stored as multiples of 10 (validation enforced in service).
 * Rupee conversion ratio: pointsPerRupee (default 10 → 10 points = ₹1).
 */
@Entity
@Table(name = "referral_config")
public class ReferralConfig {

    @Id
    private Long id = 1L;

    /** Conversion ratio: how many points equal ₹1. Default 10. */
    @Column(name = "points_per_rupee", nullable = false)
    private Integer pointsPerRupee = 10;

    // Referrer scratch card (multiples of 10)
    @Column(name = "referrer_min_points", nullable = false)
    private Integer referrerMinPoints = 10;

    @Column(name = "referrer_max_points", nullable = false)
    private Integer referrerMaxPoints = 150;

    /** Probability (0..1) that referrer reward falls in lower band [min, lowBandMax]. */
    @Column(name = "referrer_low_band_max", nullable = false)
    private Integer referrerLowBandMax = 100;

    @Column(name = "referrer_low_band_probability", nullable = false)
    private Double referrerLowBandProbability = 0.85;

    // Referee (new user) scratch card (multiples of 10)
    @Column(name = "referee_min_points", nullable = false)
    private Integer refereeMinPoints = 10;

    @Column(name = "referee_max_points", nullable = false)
    private Integer refereeMaxPoints = 500;

    // Limits
    @Column(name = "card_expiry_days", nullable = false)
    private Integer cardExpiryDays = 30;

    @Column(name = "daily_referrer_cap", nullable = false)
    private Integer dailyReferrerCap = 5;

    @Column(name = "monthly_referrer_cap", nullable = false)
    private Integer monthlyReferrerCap = 50;

    @Column(name = "fraud_block_threshold", nullable = false)
    private Integer fraudBlockThreshold = 70;

    @Column(name = "ip_signup_limit_per_hour", nullable = false)
    private Integer ipSignupLimitPerHour = 5;

    @Column(name = "device_signup_limit_per_day", nullable = false)
    private Integer deviceSignupLimitPerDay = 2;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ── getters / setters ─────────────────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getPointsPerRupee() { return pointsPerRupee; }
    public void setPointsPerRupee(Integer pointsPerRupee) { this.pointsPerRupee = pointsPerRupee; }
    public Integer getReferrerMinPoints() { return referrerMinPoints; }
    public void setReferrerMinPoints(Integer referrerMinPoints) { this.referrerMinPoints = referrerMinPoints; }
    public Integer getReferrerMaxPoints() { return referrerMaxPoints; }
    public void setReferrerMaxPoints(Integer referrerMaxPoints) { this.referrerMaxPoints = referrerMaxPoints; }
    public Integer getReferrerLowBandMax() { return referrerLowBandMax; }
    public void setReferrerLowBandMax(Integer referrerLowBandMax) { this.referrerLowBandMax = referrerLowBandMax; }
    public Double getReferrerLowBandProbability() { return referrerLowBandProbability; }
    public void setReferrerLowBandProbability(Double referrerLowBandProbability) { this.referrerLowBandProbability = referrerLowBandProbability; }
    public Integer getRefereeMinPoints() { return refereeMinPoints; }
    public void setRefereeMinPoints(Integer refereeMinPoints) { this.refereeMinPoints = refereeMinPoints; }
    public Integer getRefereeMaxPoints() { return refereeMaxPoints; }
    public void setRefereeMaxPoints(Integer refereeMaxPoints) { this.refereeMaxPoints = refereeMaxPoints; }
    public Integer getCardExpiryDays() { return cardExpiryDays; }
    public void setCardExpiryDays(Integer cardExpiryDays) { this.cardExpiryDays = cardExpiryDays; }
    public Integer getDailyReferrerCap() { return dailyReferrerCap; }
    public void setDailyReferrerCap(Integer dailyReferrerCap) { this.dailyReferrerCap = dailyReferrerCap; }
    public Integer getMonthlyReferrerCap() { return monthlyReferrerCap; }
    public void setMonthlyReferrerCap(Integer monthlyReferrerCap) { this.monthlyReferrerCap = monthlyReferrerCap; }
    public Integer getFraudBlockThreshold() { return fraudBlockThreshold; }
    public void setFraudBlockThreshold(Integer fraudBlockThreshold) { this.fraudBlockThreshold = fraudBlockThreshold; }
    public Integer getIpSignupLimitPerHour() { return ipSignupLimitPerHour; }
    public void setIpSignupLimitPerHour(Integer ipSignupLimitPerHour) { this.ipSignupLimitPerHour = ipSignupLimitPerHour; }
    public Integer getDeviceSignupLimitPerDay() { return deviceSignupLimitPerDay; }
    public void setDeviceSignupLimitPerDay(Integer deviceSignupLimitPerDay) { this.deviceSignupLimitPerDay = deviceSignupLimitPerDay; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
