package com.project.Anusha.service;

import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Product;
import com.project.Anusha.model.Wishlist;
import com.project.Anusha.repository.ProductRepository;
import com.project.Anusha.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class WishlistService {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private ProductRepository productRepository;

    public void addToWishlist(Customer customer, Long productId) {

        if (wishlistRepository.existsByCustomerIdAndProductId(customer.getId(), productId)) {
            return;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Wishlist wishlist = new Wishlist();
        wishlist.setCustomer(customer);
        wishlist.setProduct(product);
        wishlistRepository.save(wishlist);
    }

    public void mergeWishlist(Customer customer, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return;
        for (Long productId : productIds) {
            try {
                addToWishlist(customer, productId);
            } catch (Exception e) {
                // skip invalid products
            }
        }
    }

    public void removeFromWishlist(Customer customer, Long productId) {
        Optional<Wishlist> wishlist = wishlistRepository.findByCustomerIdAndProductId(customer.getId(), productId);
        wishlist.ifPresent(wishlistRepository::delete);
    }

    public List<Wishlist> getCustomerWishlist(Customer customer) {
        return wishlistRepository.findByCustomerId(customer.getId());
    }

    public boolean isWishlisted(Customer customer, Long productId) {
        return wishlistRepository.existsByCustomerIdAndProductId(customer.getId(), productId);
    }

}
