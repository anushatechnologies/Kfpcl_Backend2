package com.project.Anusha.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CartItemDto {
    private Long id;
    private Long variantId;
    private String variantName;       // e.g., "250ml"
    private Long productId;
    private String productName;
    private String productImage;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;     // unitPrice * quantity
    private Boolean freeItem;
    private String offerName;
}
