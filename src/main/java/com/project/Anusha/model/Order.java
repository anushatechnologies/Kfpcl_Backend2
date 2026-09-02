package com.project.Anusha.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_customer_idempotency", columnNames = {"customer_id", "idempotency_key"})
        }
)
@Getter
@Setter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal deliveryCharge = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal platformFee = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal handlingCharge = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal smallCartFee = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal grandTotal;

    @Column(length = 20)
    private String paymentMethod; // COD, ONLINE, WALLET, COD_WALLET, ONLINE_WALLET

    @Column(precision = 10, scale = 2)
    private BigDecimal walletAmount = BigDecimal.ZERO; // wallet portion applied to this order

    @Column(length = 20)
    private String paymentStatus = "pending";

    @Column(length = 20)
    private String orderStatus = "pending";

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    private LocalDateTime placedAt = LocalDateTime.now();
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    private void ensureOrderNumber() {
        if (orderNumber == null || orderNumber.isBlank()) {
            orderNumber = "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
    }
}
