package com.project.Anusha.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VariantRequest {
    private Long id;               // existing variant ID for updates
    private String name;           // e.g., "250ml"
    private String sku;
    @JsonAlias("mrp")
    private Double price;
    @JsonAlias("sellingPrice")
    private Double discountPrice;
    private Integer stock;     
    private Boolean isActive;
    private Integer displayOrder;
}
