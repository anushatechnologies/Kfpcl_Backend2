package com.project.Anusha.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Data
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    private String transactionId;

    @Column(length = 20)
    private String paymentMethod;

    @Column(name = "razorpay_payment_id", length = 100)
    private String gatewayPaymentId; // stores cf_payment_id / razorpay_payment_id

    @Column(length = 100)
    private String gatewayOrderId;   // cf_order_id / razorpay_order_id

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 20)
    private String status; // pending, success, failed

    @Column(columnDefinition = "json")
    private String gatewayResponse; // store as JSON string

    @Column(length = 100)
    private String refundId;

    @Column(precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(length = 30)
    private String refundStatus;

    private String refundReason;
    private LocalDateTime refundedAt;

    private LocalDateTime paymentDate;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
