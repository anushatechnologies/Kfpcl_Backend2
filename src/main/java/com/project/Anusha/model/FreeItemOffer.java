package com.project.Anusha.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "free_item_offers")
@Data
public class FreeItemOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qualifying_variant_id", nullable = false)
    private Variant qualifyingVariant;

    @Column(nullable = false)
    private Integer qualifyingQuantity = 1;

    /**
     * When true, the "buy quantity" is counted across all variants of the qualifying product
     * (i.e., the product that {@link #qualifyingVariant} belongs to). When false, only the
     * exact qualifying variant counts.
     */
    @Column(name = "qualifying_by_product", nullable = false)
    private boolean qualifyingByProduct = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "free_variant_id", nullable = false)
    private Variant freeVariant;

    @Column(nullable = false)
    private Integer freeQuantity = 1;

    private LocalDateTime startDate;
    private LocalDateTime expiryDate;

    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
