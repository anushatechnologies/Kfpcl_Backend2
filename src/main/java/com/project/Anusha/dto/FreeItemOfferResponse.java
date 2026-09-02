package com.project.Anusha.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FreeItemOfferResponse {
    private Long id;
    private String name;
    private String description;
    private Long qualifyingVariantId;
    private Long qualifyingProductId;
    private String qualifyingProductName;
    private String qualifyingProductImage;
    private String qualifyingVariantName;
    private String qualifyingVariantSku;
    private Integer qualifyingQuantity;
    private boolean qualifyingByProduct;
    private Long freeVariantId;
    private Long freeProductId;
    private String freeProductName;
    private String freeProductImage;
    private String freeVariantName;
    private String freeVariantSku;
    private Integer freeQuantity;
    private LocalDateTime startDate;
    private LocalDateTime expiryDate;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
