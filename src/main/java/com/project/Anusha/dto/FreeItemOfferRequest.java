package com.project.Anusha.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FreeItemOfferRequest {
    private String name;
    private String description;
    private Long qualifyingVariantId;
    private Integer qualifyingQuantity;
    private Boolean qualifyingByProduct;
    private Long freeVariantId;
    private Integer freeQuantity;
    private LocalDateTime startDate;
    private LocalDateTime expiryDate;
    private Boolean active;
}
