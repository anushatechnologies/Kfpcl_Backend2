package com.project.Anusha.service;

import com.project.Anusha.dto.RatingRequest;
import com.project.Anusha.model.Product;
import com.project.Anusha.model.ProductRating;
import com.project.Anusha.repository.ProductRatingRepository;
import com.project.Anusha.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RatingService {

    @Autowired
    private ProductRatingRepository ratingRepository;

    @Autowired
    private ProductRepository productRepository;

    public ProductRating addRating(RatingRequest request) {
        Optional<Product> productOpt = productRepository.findById(request.getProductId());
        if (productOpt.isEmpty()) {
            throw new IllegalArgumentException("Product not found with id: " + request.getProductId());
        }
        Long buyerId = request.getBuyerId() != null ? request.getBuyerId() : 1L;

        ProductRating rating = new ProductRating(
                buyerId,
                productOpt.get(),
                request.getRating() != null ? request.getRating() : 5,
                request.getReview()
        );
        return ratingRepository.save(rating);
    }
}
