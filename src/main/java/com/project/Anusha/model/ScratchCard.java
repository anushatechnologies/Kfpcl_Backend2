package com.project.Anusha.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A scratch card issued to a user. Reward points are revealed when scratched
 * (NOT generated at creation time — prevents leak via API/DB read before reveal).
 *
 * Points are always multiples of 10 so the rupee conversion is integer-clean
 * (10 points = ₹1 → e.g. 80 points = ₹8).
 */
@Entity
@Table(name = "scratch_cards",
        indexes = {
                @Index(name = "idx_sc_user_status", columnList = "user_main_id,status"),
                @Index(name = "idx_sc_referral", columnList = "referral_id")
        })
public class ScratchCard {

    public enum Type {
        REFERRER_REWARD,   // given to person who referred
        REFEREE_SIGNUP,    // given to new user who signed up via referral
        ADMIN_GIFT         // manually issued by admin
    }

    public enum Status {
        AVAILABLE,   // ready to scratch
        SCRATCHED,   // already revealed, points credited
        EXPIRED,     // past expiry, never scratched
        REVOKED      // admin revoked
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_main_id", nullable = false)
    private UserMain owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.AVAILABLE;

    /** Source referral if this card was issued from one. Null for admin gifts. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_id")
    private Referral referral;

    /** Min points (inclusive). Always a multiple of 10. */
    @Column(name = "min_points", nullable = false)
    private Integer minPoints;

    /** Max points (inclusive). Always a multiple of 10. */
    @Column(name = "max_points", nullable = false)
    private Integer maxPoints;

    /** Filled at scratch time. Always a multiple of 10. */
    @Column(name = "revealed_points")
    private Integer revealedPoints;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "subtitle", length = 200)
    private String subtitle;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "scratched_at")
    private LocalDateTime scratchedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    // ── getters / setters ─────────────────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserMain getOwner() { return owner; }
    public void setOwner(UserMain owner) { this.owner = owner; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Referral getReferral() { return referral; }
    public void setReferral(Referral referral) { this.referral = referral; }
    public Integer getMinPoints() { return minPoints; }
    public void setMinPoints(Integer minPoints) { this.minPoints = minPoints; }
    public Integer getMaxPoints() { return maxPoints; }
    public void setMaxPoints(Integer maxPoints) { this.maxPoints = maxPoints; }
    public Integer getRevealedPoints() { return revealedPoints; }
    public void setRevealedPoints(Integer revealedPoints) { this.revealedPoints = revealedPoints; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getScratchedAt() { return scratchedAt; }
    public void setScratchedAt(LocalDateTime scratchedAt) { this.scratchedAt = scratchedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
