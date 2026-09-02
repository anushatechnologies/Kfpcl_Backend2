package com.project.Anusha.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminBulkProductActionRequest {
    private List<Long> productIds;
    private String action;
    private Long storeId;
    private Long categoryId;
    private Long subCategoryId;
    private Integer displayOrder;
    private Integer displayOrderDelta;
    private Double percentage;
    private Integer stock;
}
