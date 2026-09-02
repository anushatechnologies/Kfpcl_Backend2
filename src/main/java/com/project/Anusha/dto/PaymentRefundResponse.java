package com.project.Anusha.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PaymentRefundResponse {
    private boolean success;
    private Long orderId;
    private String orderNumber;
    private String paymentMethod;
    private String paymentStatus;
    private String refundStatus;
    private String refundId;
    private BigDecimal refundAmount;
    private String refundReason;
    private LocalDateTime refundedAt;
    private String message;
}
