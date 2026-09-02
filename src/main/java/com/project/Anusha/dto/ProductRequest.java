package com.project.Anusha.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRequest {
    private String name;
    private String description;
    private Boolean isActive;
    @JsonAlias("trending")
    private Boolean isTrending;
    private Boolean bestSeller;
    private Boolean isDraft;
    private Integer displayOrder;
    private LocalDateTime publishAt;
    private LocalDateTime unpublishAt;
    private Boolean flashSaleEnabled;
    private LocalDateTime flashSaleStartAt;
    private LocalDateTime flashSaleEndAt;
    private Double flashSalePrice;
    private String hsnCode;
    private BigDecimal gstRate;
    private Long categoryId;
    private Long subCategoryId;
    @NotNull(message = "Variants are required")
    private List<VariantRequest> variants;
    private Long storeId;          // new field
    // image/video would be handled separately via multipart file
}
