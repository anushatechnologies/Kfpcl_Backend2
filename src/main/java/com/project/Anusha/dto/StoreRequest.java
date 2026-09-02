package com.project.Anusha.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StoreRequest {
    @NotBlank
    private String name;

    private String label;
    private Integer displayOrder = 0;
    private Boolean active = true;

    private String address;
    private String city;
    private String pincode;
    private String phoneNumber;
    private String priceRange;
    private String timings;
    private String announcement;
    private String delivery;
    private String packageCost;
    private Double rating;
    private Integer preferredOrder = 0;
    private Double latitude;
    private Double longitude;
}