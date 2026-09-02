package com.project.Anusha.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private Boolean isActive;
    private Boolean isTrending;
    private Boolean bestSeller;
    private Boolean isDraft;
    private Integer displayOrder;
    private String imageUrl;
    private String videoUrl;
    private LocalDateTime publishAt;
    private LocalDateTime unpublishAt;
    private Boolean flashSaleEnabled;
    private LocalDateTime flashSaleStartAt;
    private LocalDateTime flashSaleEndAt;
    private Double flashSalePrice;
    private String hsnCode;
    private BigDecimal gstRate;
    private Boolean flashSaleActive;
    private Long categoryId;
    private String categoryName;
    private Long subCategoryId;
    private String subCategoryName;
    private Long storeId;            // new field
    private String storeName;        // optional, for convenience
    private List<ProductImageResponse> images;
    private List<VariantResponse> variants;
    private Double minPrice;
    private Double maxPrice;
}
