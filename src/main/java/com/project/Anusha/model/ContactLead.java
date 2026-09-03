package com.project.Anusha.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contact_leads")
public class ContactLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "contact_type", nullable = false, length = 20)
    private String contactType; // 'CALL' or 'WHATSAPP'

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    @PrePersist
    public void prePersist() {
        if (clickedAt == null) {
            clickedAt = LocalDateTime.now();
        }
    }

    public ContactLead() {}

    public ContactLead(Long buyerId, Product product, Supplier supplier, String contactType) {
        this.buyerId = buyerId;
        this.product = product;
        this.supplier = supplier;
        this.contactType = contactType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }

    public String getContactType() { return contactType; }
    public void setContactType(String contactType) { this.contactType = contactType; }

    public LocalDateTime getClickedAt() { return clickedAt; }
    public void setClickedAt(LocalDateTime clickedAt) { this.clickedAt = clickedAt; }
}
