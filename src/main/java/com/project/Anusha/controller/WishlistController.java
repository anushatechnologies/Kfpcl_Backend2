package com.project.Anusha.controller;

import com.project.Anusha.dto.WishlistRequest;
import com.project.Anusha.model.Product;
import com.project.Anusha.model.Wishlist;
import com.project.Anusha.service.WishlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/products/wishlist")
@CrossOrigin(origins = "*")
public class WishlistController {

    @Autowired
    private WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<List<Product>> getWishlist(@RequestParam(name = "buyerId", defaultValue = "1") Long buyerId) {
        return ResponseEntity.ok(wishlistService.getWishlistProducts(buyerId));
    }

    @PostMapping
    public ResponseEntity<?> addToWishlist(@RequestBody WishlistRequest request) {
        try {
            Long buyerId = request.getBuyerId() != null ? request.getBuyerId() : 1L;
            Wishlist wishlist = wishlistService.addToWishlist(buyerId, request.getProductId());
            return ResponseEntity.ok(wishlist);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<?> removeFromWishlist(
            @RequestParam(name = "buyerId", defaultValue = "1") Long buyerId,
            @RequestParam(name = "productId") Long productId) {
        wishlistService.removeFromWishlist(buyerId, productId);
        return ResponseEntity.ok("Product removed from wishlist");
    }
}
