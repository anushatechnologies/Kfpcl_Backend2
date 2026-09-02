package com.project.Anusha.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CartResponse {
    private Long cartId;
    private List<CartItemDto> items;
    private List<CartItemDto> freeItems;
    private BigDecimal subtotal;
    private BigDecimal estimatedDeliveryCharge;
    private BigDecimal deliveryCharge;
    private BigDecimal platformFee;
    private BigDecimal grandTotal;
}
