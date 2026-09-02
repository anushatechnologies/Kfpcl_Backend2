package com.project.Anusha.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminOrderDetailDto {
    private Long id;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String customerEmail;

    /** Safe DTO — avoids lazy-loading & circular-reference issues from the Address entity */
    private OrderResponse.AddressSummaryDto address;

    private BigDecimal subtotal;
    private BigDecimal deliveryCharge;
    private BigDecimal platformFee;
    private BigDecimal handlingCharge;
    private BigDecimal smallCartFee;
    private BigDecimal discount;
    private BigDecimal taxableAmount;
    private BigDecimal tax;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal walletApplied;
    private BigDecimal paidAmount;
    private BigDecimal grandTotal;
    private String orderStatus;
    private String paymentStatus;
    private String paymentMethod;
    private String refundStatus;
    private String refundId;
    private BigDecimal refundAmount;
    private String refundReason;
    private LocalDateTime refundedAt;
    private LocalDateTime placedAt;

    /** Assigned delivery person details (null until a rider is assigned) */
    private String deliveryPersonName;
    private String deliveryPersonPhone;
    private String deliveryPersonVehicle;
    private Double deliveryPersonRating;

    /** ETA forwarded from DeliveryOrder.estimatedDeliveryTime */
    private String estimatedDeliveryTime;

    /**
     * Store acceptance status from DeliveryOrder:
     * STORE_NOTIFIED | STORE_ACCEPTED | STORE_REJECTED | PICKUP_OTP_GENERATED | etc.
     */
    private String storeStatus;
    private String sellerName;
    private String sellerGstin;
    private String sellerAddress;
    private String sellerState;
    private String sellerStateCode;
    private String placeOfSupply;
    private String placeOfSupplyCode;
    private Boolean reverseCharge;

    private List<OrderResponse.OrderItemDto> items;
    private List<StoreGroupDto> storeGroups;

    @Data
    public static class StoreGroupDto {
        private Long storeId;
        private String storeName;
        private String storePhone;
        /** Current sub-order status from StoreSubOrder (PENDING / PICKED_UP / etc.) */
        private String status;
        private BigDecimal subtotal;
        private List<OrderResponse.OrderItemDto> items;
    }
}
