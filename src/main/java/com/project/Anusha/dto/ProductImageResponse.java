package com.project.Anusha.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductImageResponse {
    private Long id;
    private String imageUrl;
    private Integer displayOrder;
    private LocalDateTime createdAt;
}
