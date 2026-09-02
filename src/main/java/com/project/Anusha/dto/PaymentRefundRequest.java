package com.project.Anusha.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentRefundRequest {
    private Long orderId;
    private BigDecimal amount;
    private String reason;
}
