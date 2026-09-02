package com.project.Anusha.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminOrderSearchResult(
        Long id,
        String orderNumber,
        String customerName,
        String customerPhone,
        BigDecimal grandTotal,
        String orderStatus,
        LocalDateTime placedAt
) {
}

