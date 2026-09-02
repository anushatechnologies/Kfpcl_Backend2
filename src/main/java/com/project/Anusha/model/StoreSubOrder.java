package com.project.Anusha.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4 — Multi-store order splitting.
 * When a customer order contains items from multiple stores,
 * one StoreSubOrder is created per store.
 * Each sub-order gets its own WhatsApp OTP sent to the store.
 */
@Entity
@Table(name = "store_sub_orders")
public class StoreSubOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The parent customer order */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** The store fulfilling this sub-order */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** 4-digit OTP sent to the store via WhatsApp for rider pickup verification */
    @Column(name = "pickup_otp", length = 6)
    private String pickupOtp;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    /** Comma-separated snapshot of item names in this sub-order */
    @Column(name = "item_summary", length = 500)
    private String itemSummary;

    @Column(name = "subtotal", precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubOrderStatus status = SubOrderStatus.PENDING;

    @Column(name = "store_notified_at")
    private LocalDateTime storeNotifiedAt;

    @Column(name = "store_accepted_at")
    private LocalDateTime storeAcceptedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum SubOrderStatus {
        PENDING,
        STORE_NOTIFIED,
        STORE_ACCEPTED,
        STORE_REJECTED,
        OTP_SENT,
        PICKED_UP,
        CANCELLED
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }

    public String getPickupOtp() { return pickupOtp; }
    public void setPickupOtp(String pickupOtp) { this.pickupOtp = pickupOtp; }

    public LocalDateTime getOtpExpiry() { return otpExpiry; }
    public void setOtpExpiry(LocalDateTime otpExpiry) { this.otpExpiry = otpExpiry; }

    public String getItemSummary() { return itemSummary; }
    public void setItemSummary(String itemSummary) { this.itemSummary = itemSummary; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public SubOrderStatus getStatus() { return status; }
    public void setStatus(SubOrderStatus status) { this.status = status; }

    public LocalDateTime getStoreNotifiedAt() { return storeNotifiedAt; }
    public void setStoreNotifiedAt(LocalDateTime storeNotifiedAt) { this.storeNotifiedAt = storeNotifiedAt; }

    public LocalDateTime getStoreAcceptedAt() { return storeAcceptedAt; }
    public void setStoreAcceptedAt(LocalDateTime storeAcceptedAt) { this.storeAcceptedAt = storeAcceptedAt; }

    public LocalDateTime getPickedUpAt() { return pickedUpAt; }
    public void setPickedUpAt(LocalDateTime pickedUpAt) { this.pickedUpAt = pickedUpAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
