package com.project.Anusha.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CheckoutSettingsResponse {
    private Long id;
    private BigDecimal deliveryCharge;
    private BigDecimal platformFee;
    private BigDecimal handlingCharge;
    private BigDecimal smallCartFee;
    private BigDecimal smallCartThreshold;
    private boolean onlinePaymentEnabled;
    private boolean cashOnDeliveryEnabled;
    private LocalDateTime updatedAt;
}
