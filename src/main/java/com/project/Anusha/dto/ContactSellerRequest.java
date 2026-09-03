package com.project.Anusha.dto;

public class ContactSellerRequest {
    private Long productId;
    private String supplierId;
    private String contactType; // 'CALL' or 'WHATSAPP'
    private Long buyerId;

    public ContactSellerRequest() {}

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public String getContactType() { return contactType; }
    public void setContactType(String contactType) { this.contactType = contactType; }

    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
}
