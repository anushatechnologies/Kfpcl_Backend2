package com.project.Anusha.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentInitiateRequest {
    private Long orderId;
    private BigDecimal walletAmount; // wallet portion to deduct from Razorpay charge (optional, defaults to 0)
}