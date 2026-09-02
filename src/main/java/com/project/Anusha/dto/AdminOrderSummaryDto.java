package com.project.Anusha.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminOrderSummaryDto {
    private Long id;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
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
    private BigDecimal grandTotal;
    private String paymentMethod;
    private String orderStatus;
    private String paymentStatus;
    private LocalDateTime placedAt;
    /** True only when the linked DeliveryOrder has a rider attached */
    private boolean riderAssigned;
    /** Helpful for admin UI quick actions */
    private String deliveryPersonName;
    /** Names of stores fulfilling items in this order */
    private List<String> storeNames;
    /** IDs of stores fulfilling items in this order */
    private List<Long> storeIds;
    /** Number of distinct stores — quick multi-store indicator */
    private int storeCount;
}
