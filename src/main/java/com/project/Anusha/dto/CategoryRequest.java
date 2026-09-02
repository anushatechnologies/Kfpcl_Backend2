package com.project.Anusha.dto;

import lombok.Data;

@Data
public class CategoryRequest {
    private String name;
    private String description;
    private Boolean isActive;
    private Integer displayOrder;
    private Double discount;
    private String imageUrl;
    private String videoUrl;
}