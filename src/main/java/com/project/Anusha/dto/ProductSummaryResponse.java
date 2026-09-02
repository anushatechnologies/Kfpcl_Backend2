package com.project.Anusha.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductSummaryResponse {
    private Long id;
    private String name;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;
    private Long subCategoryId;
    private String subCategoryName;
    private Long storeId;
    private String storeName;
    private Boolean isTrending;
    private Boolean bestSeller;
    private Boolean flashSaleActive;
    private Double flashSalePrice;
    private String hsnCode;
    private BigDecimal gstRate;
    private Double minPrice;
    private Double maxPrice;
    private Double price;
    private Double discountPrice;
    private Integer stock;
}
