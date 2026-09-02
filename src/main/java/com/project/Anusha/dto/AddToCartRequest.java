package com.project.Anusha.dto;

import lombok.Data;

@Data
public class AddToCartRequest {
    private Long variantId;   // was productId
    private Integer quantity;
}