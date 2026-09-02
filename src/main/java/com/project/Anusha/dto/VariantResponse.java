package com.project.Anusha.dto;

import lombok.Data;

@Data
public class VariantResponse {
    private Long id;
    private String name;
    private String sku;
    private Double price;
    private Double discountPrice;
    private Integer stock;
    private Boolean isActive;
    private Integer displayOrder;
}