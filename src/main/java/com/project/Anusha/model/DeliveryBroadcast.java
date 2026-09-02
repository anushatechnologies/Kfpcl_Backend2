package com.project.Anusha.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks a vehicle-type broadcast for first-come-first-serve delivery assignment.
 *
 * Admin broadcasts an order to all online delivery persons with a specific vehicle type.
 * The first rider to call /accept wins the assignment; the broadcast expires after 10 minutes.
 */
@Entity
@Table(name = "delivery_broadcasts")
public class DeliveryBroadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The customer order this broadcast is for */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Which vehicle type this broadcast was sent to */
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private DeliveryPerson.VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BroadcastStatus status = BroadcastStatus.PENDING;

    /** Delivery person who accepted first (null until accepted) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by_id")
    private DeliveryPerson acceptedBy;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    /** Broadcast expires after this time — riders can no longer accept */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public DeliveryBroadcast() {}

    public DeliveryBroadcast(Order order, DeliveryPerson.VehicleType vehicleType) {
        this.order = order;
        this.vehicleType = vehicleType;
        this.expiresAt = LocalDateTime.now().plusMinutes(10);
    }

    // ── enums ──────────────────────────────────────────────────────────────

    public enum BroadcastStatus {
        PENDING,    // waiting for a rider to accept
        ACCEPTED,   // a rider accepted — no further accepts allowed
        EXPIRED,    // nobody accepted before expiry
        CANCELLED   // admin cancelled the broadcast
    }

    // ── getters / setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public DeliveryPerson.VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(DeliveryPerson.VehicleType vehicleType) { this.vehicleType = vehicleType; }

    public BroadcastStatus getStatus() { return status; }
    public void setStatus(BroadcastStatus status) { this.status = status; }

    public DeliveryPerson getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(DeliveryPerson acceptedBy) { this.acceptedBy = acceptedBy; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
