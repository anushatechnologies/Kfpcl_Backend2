package com.project.Anusha.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StoreResponse {
    private Long id;
    private String name;
    private String label;
    private Integer displayOrder;
    private Boolean active;
    private String imageUrl;

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
    private Integer preferredOrder;
    private Double latitude;
    private Double longitude;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}