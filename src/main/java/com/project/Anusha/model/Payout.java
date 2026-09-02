package com.project.Anusha.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity
@Table(name = "payouts")
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_person_id", nullable = false)
    private DeliveryPerson deliveryPerson;

    @Column(name = "payout_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal payoutAmount;

    @Column(name = "week_start_date", nullable = false)
    private LocalDateTime weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDateTime weekEndDate;

    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_admin_id")
    private User processedByAdmin;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Pre-generated constructor
    public Payout() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Payout(DeliveryPerson deliveryPerson, BigDecimal payoutAmount, 
                  LocalDateTime weekStartDate, LocalDateTime weekEndDate, Integer totalOrders) {
        this();
        this.deliveryPerson = deliveryPerson;
        this.payoutAmount = payoutAmount;
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekEndDate;
        this.totalOrders = totalOrders;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DeliveryPerson getDeliveryPerson() {
        return deliveryPerson;
    }

    public void setDeliveryPerson(DeliveryPerson deliveryPerson) {
        this.deliveryPerson = deliveryPerson;
    }

    public BigDecimal getPayoutAmount() {
        return payoutAmount;
    }

    public void setPayoutAmount(BigDecimal payoutAmount) {
        this.payoutAmount = payoutAmount;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDateTime weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public LocalDateTime getWeekEndDate() {
        return weekEndDate;
    }

    public void setWeekEndDate(LocalDateTime weekEndDate) {
        this.weekEndDate = weekEndDate;
    }

    public Integer getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Integer totalOrders) {
        this.totalOrders = totalOrders;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
        this.updatedAt = LocalDateTime.now();
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
        this.updatedAt = LocalDateTime.now();
    }

    public User getProcessedByAdmin() {
        return processedByAdmin;
    }

    public void setProcessedByAdmin(User processedByAdmin) {
        this.processedByAdmin = processedByAdmin;
        this.updatedAt = LocalDateTime.now();
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Payout Status Enum
    public enum PayoutStatus {
        PENDING("Pending"),
        PROCESSED("Processed"),
        FAILED("Failed"),
        CANCELLED("Cancelled");

        private final String displayName;

        PayoutStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
