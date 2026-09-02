package com.project.Anusha.repository;

import com.project.Anusha.model.ProductRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRatingRepository extends JpaRepository<ProductRating, Long> {
    List<ProductRating> findByProductId(Long productId);
    Optional<ProductRating> findByCustomerIdAndProductId(Long customerId, Long productId);
    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ProductRating pr WHERE pr.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
