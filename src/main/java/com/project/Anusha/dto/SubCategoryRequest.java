package com.project.Anusha.dto;

import lombok.Data;

@Data
public class SubCategoryRequest {
    private String name;
    private String description;
    private Boolean isActive;
    private Integer displayOrder;
    private Double discount;
    private Long categoryId;
    private String imageUrl; // optional, if you want to set directly
    private String videoUrl;
}