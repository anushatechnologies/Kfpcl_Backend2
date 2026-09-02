package com.project.Anusha.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)   // changed from product_id
    private Variant variant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;                         // snapshot of variant price (with discount applied)

    @Column(precision = 10, scale = 2)
    private BigDecimal totalPrice;                        // unitPrice * quantity

    // Snapshots
    private String productName;                           // from Product
    private String variantName;                            // from Variant (e.g., "250ml")
    private String productSku;                             // from Variant
    private String imageUrl;                               // from Product.imageUrl (snapshot at order time)
    private Boolean freeItem = false;
    private String offerName;
    @Column(length = 20)
    private String hsnCode;
    @Column(precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;
    @Column(precision = 10, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;
    @Column(precision = 5, scale = 2)
    private BigDecimal cgstRate = BigDecimal.ZERO;
    @Column(precision = 5, scale = 2)
    private BigDecimal sgstRate = BigDecimal.ZERO;
    @Column(precision = 5, scale = 2)
    private BigDecimal igstRate = BigDecimal.ZERO;
    @Column(precision = 10, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO;
    @Column(precision = 10, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO;
    @Column(precision = 10, scale = 2)
    private BigDecimal igstAmount = BigDecimal.ZERO;
    @Column(precision = 10, scale = 2)
    private BigDecimal totalTaxAmount = BigDecimal.ZERO;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (totalPrice == null && unitPrice != null && quantity != null) {
            totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
