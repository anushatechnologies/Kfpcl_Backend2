package com.project.Anusha.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "banners")
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "target_url")
    private String targetUrl;

    @Column(name = "action_type")
    private String actionType; // e.g., CATEGORY, PRODUCT, OFFER, EXTERNAL

    @Column(name = "action_value")
    private String actionValue; // e.g., "3", "123", "https://..."

    /**
     * Which app this banner targets.
     * CUSTOMER  — shown only in the customer app
     * DELIVERY  — shown only in the delivery partner app home carousel
     * BOTH      — shown in both apps
     */
    // CUSTOMER = customer app, WEBSITE = storefront, DELIVERY = delivery app, BOTH = shared campaign
    @Column(name = "target_app", columnDefinition = "VARCHAR(20) DEFAULT 'CUSTOMER'")
    private String targetApp = "CUSTOMER"; // default keeps existing banners as customer-only

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
