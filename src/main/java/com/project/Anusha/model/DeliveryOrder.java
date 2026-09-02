package com.project.Anusha.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "delivery_orders")
public class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_person_id")
    private DeliveryPerson deliveryPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "pickup_otp")
    private String pickupOtp;

    @Column(name = "delivery_otp")
    private String deliveryOtp;

    @Column(name = "pickup_otp_expiry")
    private LocalDateTime pickupOtpExpiry;

    @Column(name = "delivery_otp_expiry")
    private LocalDateTime deliveryOtpExpiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PLACED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    @Column(nullable = false)
    private boolean isPaidOnline = false;

    @Column(name = "order_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "store_remarks")
    private String storeRemarks;

    /** Store (pickup) coordinates — copied from Store at order creation */
    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    /** Customer (delivery) coordinates — copied from Address at order creation */
    @Column(name = "delivery_latitude")
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude")
    private Double deliveryLongitude;

    /** Distance from store to customer in km — calculated via Haversine at pickup confirmation */
    @Column(name = "distance_km")
    private Double distanceKm;

    /** S3 URL of the delivery confirmation photo */
    @Column(name = "delivery_photo_url", length = 500)
    private String deliveryPhotoUrl;

    /** When delivery OTP was verified by the rider */
    @Column(name = "delivery_otp_verified_at")
    private LocalDateTime deliveryOtpVerifiedAt;

    @Column(name = "notification_sent_at")
    private LocalDateTime notificationSentAt;

    @Column(name = "pickup_otp_sent_at")
    private LocalDateTime pickupOtpSentAt;

    /** 1–5 star rating left by the customer after delivery */
    @Column(name = "customer_rating")
    private Integer customerRating;

    /** Optional text feedback from the customer */
    @Column(name = "customer_feedback", length = 500)
    private String customerFeedback;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Pre-generated constructor
    public DeliveryOrder() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public DeliveryPerson getDeliveryPerson() {
        return deliveryPerson;
    }

    public void setDeliveryPerson(DeliveryPerson deliveryPerson) {
        this.deliveryPerson = deliveryPerson;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(String pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getPickupOtp() {
        return pickupOtp;
    }

    public void setPickupOtp(String pickupOtp) {
        this.pickupOtp = pickupOtp;
    }

    public String getDeliveryOtp() {
        return deliveryOtp;
    }

    public void setDeliveryOtp(String deliveryOtp) {
        this.deliveryOtp = deliveryOtp;
    }

    public LocalDateTime getPickupOtpExpiry() {
        return pickupOtpExpiry;
    }

    public void setPickupOtpExpiry(LocalDateTime pickupOtpExpiry) {
        this.pickupOtpExpiry = pickupOtpExpiry;
    }

    public LocalDateTime getDeliveryOtpExpiry() {
        return deliveryOtpExpiry;
    }

    public void setDeliveryOtpExpiry(LocalDateTime deliveryOtpExpiry) {
        this.deliveryOtpExpiry = deliveryOtpExpiry;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public boolean isPaidOnline() {
        return isPaidOnline;
    }

    public void setPaidOnline(boolean paidOnline) {
        isPaidOnline = paidOnline;
    }

    public BigDecimal getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(BigDecimal orderAmount) {
        this.orderAmount = orderAmount;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getPickedUpAt() {
        return pickedUpAt;
    }

    public void setPickedUpAt(LocalDateTime pickedUpAt) {
        this.pickedUpAt = pickedUpAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getStoreRemarks() {
        return storeRemarks;
    }

    public void setStoreRemarks(String storeRemarks) {
        this.storeRemarks = storeRemarks;
    }

    public Double getPickupLatitude() { return pickupLatitude; }
    public void setPickupLatitude(Double pickupLatitude) { this.pickupLatitude = pickupLatitude; }
    public Double getPickupLongitude() { return pickupLongitude; }
    public void setPickupLongitude(Double pickupLongitude) { this.pickupLongitude = pickupLongitude; }
    public Double getDeliveryLatitude() { return deliveryLatitude; }
    public void setDeliveryLatitude(Double deliveryLatitude) { this.deliveryLatitude = deliveryLatitude; }
    public Double getDeliveryLongitude() { return deliveryLongitude; }
    public void setDeliveryLongitude(Double deliveryLongitude) { this.deliveryLongitude = deliveryLongitude; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public String getDeliveryPhotoUrl() { return deliveryPhotoUrl; }
    public void setDeliveryPhotoUrl(String deliveryPhotoUrl) { this.deliveryPhotoUrl = deliveryPhotoUrl; }
    public LocalDateTime getDeliveryOtpVerifiedAt() { return deliveryOtpVerifiedAt; }
    public void setDeliveryOtpVerifiedAt(LocalDateTime deliveryOtpVerifiedAt) { this.deliveryOtpVerifiedAt = deliveryOtpVerifiedAt; }

    public LocalDateTime getNotificationSentAt() {
        return notificationSentAt;
    }

    public void setNotificationSentAt(LocalDateTime notificationSentAt) {
        this.notificationSentAt = notificationSentAt;
    }

    public LocalDateTime getPickupOtpSentAt() {
        return pickupOtpSentAt;
    }

    public void setPickupOtpSentAt(LocalDateTime pickupOtpSentAt) {
        this.pickupOtpSentAt = pickupOtpSentAt;
    }

    public Integer getCustomerRating() { return customerRating; }
    public void setCustomerRating(Integer customerRating) { this.customerRating = customerRating; }

    public String getCustomerFeedback() { return customerFeedback; }
    public void setCustomerFeedback(String customerFeedback) { this.customerFeedback = customerFeedback; }

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

    // Order Status Enum
    public enum OrderStatus {
        PENDING("Pending"),
        PLACED("Order Placed"),
        STORE_NOTIFIED("Store Notified via WhatsApp"),
        STORE_ACCEPTED("Accepted by Store"),
        STORE_REJECTED("Rejected by Store"),
        PICKUP_OTP_GENERATED("Pickup OTP Generated"),
        BROADCASTED_TO_RIDERS("Available for Riders"),
        RIDER_ASSIGNED("Rider Assigned"),
        REACHED_STORE("Rider Reached Store"),
        PICKUP_OTP_VERIFIED("Pickup OTP Verified"),
        PICKED_UP("Picked Up from Store"),
        OUT_FOR_DELIVERY("In Transit to Customer"),
        DELIVERED("Delivered Successfully"),
        CANCELLED("Cancelled"),
        RIDER_REJECTED("Rejected by Rider"),
        // Legacy values kept so older rows can still be read and migrated safely.
        ASSIGNED("Legacy Assigned"),
        ACCEPTED("Legacy Accepted"),
        EN_ROUTE_TO_STORE("Legacy En Route To Store"),
        EN_ROUTE_TO_CUSTOMER("Legacy En Route To Customer");

        private final String displayName;

        OrderStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Payment Type Enum
    public enum PaymentType {
        COD("Cash on Delivery"),
        UPI("UPI Payment"),
        ONLINE_PAID("Already Paid Online"),
        ONLINE("Legacy Online Payment");

        private final String displayName;

        PaymentType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
