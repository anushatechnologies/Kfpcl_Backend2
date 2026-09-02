package com.project.Anusha.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Devices, IPs, or mobile numbers explicitly blocked from earning referral rewards.
 * Populated by automated fraud rules and admin manual entries.
 */
@Entity
@Table(name = "fraud_blocklist",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"entry_type", "entry_value"})
        },
        indexes = {
                @Index(name = "idx_fb_value", columnList = "entry_value")
        })
public class FraudBlocklist {

    public enum EntryType {
        DEVICE_ID, IP, MOBILE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EntryType entryType;

    @Column(name = "entry_value", nullable = false, length = 128)
    private String entryValue;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public EntryType getEntryType() { return entryType; }
    public void setEntryType(EntryType entryType) { this.entryType = entryType; }
    public String getEntryValue() { return entryValue; }
    public void setEntryValue(String entryValue) { this.entryValue = entryValue; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
