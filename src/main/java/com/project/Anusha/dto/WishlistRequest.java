package com.project.Anusha.dto;

public class WishlistRequest {
    private Long productId;
    private Long buyerId;

    public WishlistRequest() {}

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
}
