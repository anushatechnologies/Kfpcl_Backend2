package com.project.Anusha.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckoutSettingsUpdateRequest {
    private BigDecimal deliveryCharge;
    private BigDecimal platformFee;
    private BigDecimal handlingCharge;
    private BigDecimal smallCartFee;
    private BigDecimal smallCartThreshold;
    private Boolean onlinePaymentEnabled;
    private Boolean cashOnDeliveryEnabled;
}
