package com.project.Anusha.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SubCategoryResponse {
    private Long id;
    private String name;
    private String description;
    private Boolean isActive;
    private Integer displayOrder;
    private Double discount;
    private Long categoryId;
    private String categoryName;
    private String imageUrl;      // <-- NEW
    private String videoUrl;      // <-- NEW
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}