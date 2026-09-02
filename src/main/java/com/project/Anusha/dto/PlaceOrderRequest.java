package com.project.Anusha.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class PlaceOrderRequest {
    @NotNull(message = "addressId is required")
    private Long addressId;

    @NotBlank(message = "paymentMethod is required")
    @Pattern(
            regexp = "^(?i)(COD|ONLINE|WALLET|COD_WALLET|ONLINE_WALLET)$",
            message = "paymentMethod must be one of COD, ONLINE, WALLET, COD_WALLET, ONLINE_WALLET")
    private String paymentMethod;

    private java.math.BigDecimal walletAmount; // wallet portion for COD_WALLET / ONLINE_WALLET

    private String couponCode;
}
