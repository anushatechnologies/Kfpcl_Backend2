package com.project.Anusha.controller;

import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Product;
import com.project.Anusha.model.ProductRating;
import com.project.Anusha.model.Wishlist;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.ProductService;
import com.project.Anusha.service.ProductRatingService;
import com.project.Anusha.service.WishlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/customer/products")
@CrossOrigin(origins = "*")
public class CustomerProductActionsController {

    @Autowired
    private WishlistService wishlistService;

    @Autowired
    private ProductRatingService ratingService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    private Customer getCustomer(UserDetails userDetails) {
        return customerService.getCustomerByPhone(userDetails.getUsername());
    }

    // ---------------- Wishlist ----------------

    @PostMapping("/wishlist")
    public ResponseEntity<?> addToWishlist(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestBody Map<String, Long> request) {
        Customer customer = getCustomer(userDetails);
        Long productId = request.get("productId");
        
        if (productId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Product ID is required"));
        }
        
        wishlistService.addToWishlist(customer, productId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Product added to wishlist"));
    }

    @PostMapping("/wishlist/merge")
    public ResponseEntity<?> mergeWishlist(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestBody Map<String, List<Long>> request) {
        Customer customer = getCustomer(userDetails);
        List<Long> productIds = request.get("productIds");
        wishlistService.mergeWishlist(customer, productIds);
        return ResponseEntity.ok(Map.of("success", true, "message", "Wishlist merged successfully"));
    }

    @DeleteMapping("/wishlist")
    public ResponseEntity<?> removeFromWishlist(@AuthenticationPrincipal UserDetails userDetails,
                                                @RequestParam Long productId) {
        Customer customer = getCustomer(userDetails);
        wishlistService.removeFromWishlist(customer, productId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Product removed from wishlist"));
    }

    @GetMapping("/wishlist")
    public ResponseEntity<?> getWishlist(@AuthenticationPrincipal UserDetails userDetails) {
        Customer customer = getCustomer(userDetails);
        return ResponseEntity.ok(buildWishlistResponse(customer));
    }

    @GetMapping("/wishlist/{customerId}")
    public ResponseEntity<?> getWishlistCompat(@AuthenticationPrincipal UserDetails userDetails,
                                               @PathVariable Long customerId) {
        Customer customer = getCustomer(userDetails);
        if (!customer.getId().equals(customerId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(buildWishlistResponse(customer));
    }

    // ---------------- Rating ----------------

    @PostMapping("/rating")
    public ResponseEntity<?> addRating(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestBody Map<String, Object> request) {
        Customer customer = getCustomer(userDetails);
        Long productId = ((Number) request.get("productId")).longValue();
        int rating = ((Number) request.get("rating")).intValue();
        String comment = (String) request.get("comment");

        try {
            ratingService.addRating(customer, productId, rating, comment);
            return ResponseEntity.ok(Map.of("success", true, "message", "Rating submitted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    @GetMapping("/{productId}/ratings")
    public ResponseEntity<?> getRatings(@PathVariable Long productId) {
        List<ProductRating> ratings = ratingService.getProductRatings(productId);
        return ResponseEntity.ok(Map.of("success", true, "ratings", ratings));
    }

    @GetMapping("/rating/{productId}")
    public ResponseEntity<List<ProductRating>> getRatingsCompat(@PathVariable Long productId) {
        return ResponseEntity.ok(ratingService.getProductRatings(productId));
    }

    private List<com.project.Anusha.dto.ProductResponse> buildWishlistResponse(Customer customer) {
        List<Wishlist> wishlist = wishlistService.getCustomerWishlist(customer);
        List<Product> products = wishlist.stream()
                .map(Wishlist::getProduct)
                .collect(Collectors.toList());
        return products.stream()
                .map(productService::mapToResponse)
                .collect(Collectors.toList());
    }
}
