package com.project.Anusha.service;

import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Product;
import com.project.Anusha.model.ProductRating;
import com.project.Anusha.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Service
@Transactional
public class ProductRatingService {

    @Autowired
    private ProductRatingRepository ratingRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;


    public void addRating(Customer customer, Long productId, int ratingValue, String comment) {
        if (ratingValue < 1 || ratingValue > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        // 🔥 CRITICAL: Verified Purchase Check
        boolean isDelivered = orderItemRepository.hasDeliveredOrder(customer, product);
        if (!isDelivered) {
            throw new IllegalArgumentException("You can only rate products that have been delivered to you.");
        }

        // If user already rated, we update it
        Optional<ProductRating> existingRating = ratingRepository.findByCustomerIdAndProductId(customer.getId(), productId);

        
        if (existingRating.isPresent()) {
            ProductRating pr = existingRating.get();
            pr.setRating(ratingValue);
            pr.setComment(comment);
            ratingRepository.save(pr);
            
            // Re-calculate average rating for product
            recalculateAverageRating(product);
        } else {
            ProductRating pr = new ProductRating();
            pr.setCustomer(customer);
            pr.setProduct(product);
            pr.setRating(ratingValue);
            pr.setComment(comment);
            ratingRepository.save(pr);
            
            // Efficiently update the average rating using the formula provided
            // new_avg = ((old_avg * old_count) + new_rating) / (old_count + 1)
            double oldAvg = product.getAverageRating() != null ? product.getAverageRating() : 0.0;
            long oldCount = product.getRatingCount() != null ? product.getRatingCount() : 0L;
            
            double newAvg = ((oldAvg * oldCount) + ratingValue) / (double)(oldCount + 1);
            
            product.setAverageRating(newAvg);
            product.setRatingCount(oldCount + 1);
            productRepository.save(product);
        }
    }

    private void recalculateAverageRating(Product product) {
        List<ProductRating> allRatings = ratingRepository.findByProductId(product.getId());
        if (allRatings.isEmpty()) {
            product.setAverageRating(0.0);
            product.setRatingCount(0L);
        } else {
            double sum = 0;
            for (ProductRating pr : allRatings) {
                sum += pr.getRating();
            }
            product.setAverageRating(sum / allRatings.size());
            product.setRatingCount((long) allRatings.size());
        }
        productRepository.save(product);
    }

    public List<ProductRating> getProductRatings(Long productId) {
        return ratingRepository.findByProductId(productId);
    }
}
