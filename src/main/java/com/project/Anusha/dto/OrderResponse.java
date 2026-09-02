package com.project.Anusha.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private BigDecimal subtotal;
    private BigDecimal deliveryCharge;
    private BigDecimal platformFee;
    private BigDecimal handlingCharge;
    private BigDecimal smallCartFee;
    private BigDecimal walletApplied;
    private BigDecimal paidAmount;
    private BigDecimal tax;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal discount;
    private BigDecimal grandTotal;
    private String paymentMethod;
    private String orderStatus;
    private String status;
    private String paymentStatus;
    private String refundStatus;
    private String refundId;
    private BigDecimal refundAmount;
    private String refundReason;
    private LocalDateTime refundedAt;
    private LocalDateTime placedAt;
    private LocalDateTime createdAt;
    private AddressSummaryDto address;
    private List<OrderItemDto> items;
    /** Populated when the order spans multiple stores — one entry per store */
    private List<StoreGroupDto> storeGroups;
    /** ISO timestamp — ETA for delivery, forwarded from DeliveryOrder.assignedAt + 45 min */
    private String estimatedDeliveryTime;
    /** Assigned rider name (shown in customer tracking) */
    private String deliveryPersonName;
    private String deliveryPersonPhone;
    private String sellerName;
    private String sellerGstin;
    private String sellerAddress;
    private String sellerState;
    private String sellerStateCode;
    private String placeOfSupply;
    private String placeOfSupplyCode;
    private Boolean reverseCharge;

    @Data
    public static class AddressSummaryDto {
        private Long id;
        private String addressType;
        private String flatNumber;
        private String addressLine1;
        private String addressLine2;
        private String landmark;
        private String city;
        private String state;
        private String postalCode;
    }

    @Data
    public static class OrderItemDto {
        private Long id;
        private Long productId;
        private Long variantId;
        private String productName;
        private String variantName;      // e.g., "250ml"
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private String hsnCode;
        private BigDecimal gstRate;
        private BigDecimal taxableAmount;
        private BigDecimal cgstRate;
        private BigDecimal sgstRate;
        private BigDecimal igstRate;
        private BigDecimal cgstAmount;
        private BigDecimal sgstAmount;
        private BigDecimal igstAmount;
        private BigDecimal totalTaxAmount;
        private String imageUrl;         // product image snapshot
        private String storeName;        // which store this item belongs to
        private Boolean freeItem;
        private String offerName;
    }

    @Data
    public static class StoreGroupDto {
        private Long storeId;
        private String storeName;
        private String status;           // sub-order status from StoreSubOrder
        private BigDecimal subtotal;
        private List<OrderItemDto> items;
    }
}
