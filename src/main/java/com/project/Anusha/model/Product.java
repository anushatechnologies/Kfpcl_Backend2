package com.project.Anusha.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    

    private Boolean isActive = true;
    private Boolean deleted = false;
    private Boolean isTrending = false;
    private Boolean bestSeller = false;
    private Boolean isDraft = false;

    private Integer displayOrder;
    private LocalDateTime publishAt;
    private LocalDateTime unpublishAt;
    private Boolean flashSaleEnabled = false;
    private LocalDateTime flashSaleStartAt;
    private LocalDateTime flashSaleEndAt;
    private Double flashSalePrice;
    @Column(length = 20)
    private String hsnCode;
    @Column(precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;

    private String imageUrl;
    private String videoUrl;
    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)   // ← add nullable=false
    private Store store;


    @ManyToOne
    @JoinColumn(name = "category_id")
    @JsonIgnore 
    private Category category;

    @ManyToOne
    @JoinColumn(name = "sub_category_id")
    private SubCategory subCategory;
    
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Variant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    private Double averageRating = 0.0;
    private Long ratingCount = 0L;


    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
