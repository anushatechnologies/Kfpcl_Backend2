package com.project.Anusha.service;

import com.project.Anusha.model.Product;
import com.project.Anusha.model.Wishlist;
import com.project.Anusha.repository.ProductRepository;
import com.project.Anusha.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WishlistService {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private ProductRepository productRepository;

    public List<Product> getWishlistProducts(Long buyerId) {
        return wishlistRepository.findByBuyerId(buyerId)
                .stream()
                .map(Wishlist::getProduct)
                .collect(Collectors.toList());
    }

    public Wishlist addToWishlist(Long buyerId, Long productId) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            throw new IllegalArgumentException("Product not found with id: " + productId);
        }
        Optional<Wishlist> existing = wishlistRepository.findByBuyerIdAndProductId(buyerId, productId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Wishlist wishlist = new Wishlist(buyerId, productOpt.get());
        return wishlistRepository.save(wishlist);
    }

    @Transactional
    public void removeFromWishlist(Long buyerId, Long productId) {
        wishlistRepository.deleteByBuyerIdAndProductId(buyerId, productId);
    }
}
