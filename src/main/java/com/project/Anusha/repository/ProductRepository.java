package com.project.Anusha.repository;

import com.project.Anusha.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findTop10ByNameContainingIgnoreCaseAndIsActiveTrue(String name);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:subcategoryId IS NULL OR p.subcategory.id = :subcategoryId) AND " +
           "(:minPrice IS NULL OR p.numericPrice >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.numericPrice <= :maxPrice) AND " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) OR " +
           "LOWER(p.brand) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) OR " +
           "LOWER(p.specifications) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))")
    Page<Product> filterProducts(@Param("categoryId") Long categoryId,
                                 @Param("subcategoryId") Long subcategoryId,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 @Param("query") String query,
                                 Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
           "LOWER(p.brand) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
           "LOWER(p.specifications) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
           "LOWER(p.category.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))")
    List<Product> fullTextSearch(@Param("q") String query);

    @Query("SELECT p FROM Product p LEFT JOIN ContactLead l ON p.id = l.product.id WHERE p.isActive = true GROUP BY p.id, p.name, p.brand, p.category, p.subcategory, p.supplier, p.mainImageUrl, p.minOrderQuantity, p.indicativePrice, p.numericPrice, p.specifications, p.isActive, p.createdAt ORDER BY COUNT(l) DESC, p.id DESC")
    List<Product> findTrendingProducts(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isActive = true ORDER BY p.id DESC")
    List<Product> findBestsellers(Pageable pageable);
}
