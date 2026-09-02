package com.project.Anusha.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.Anusha.model.Category;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();
    List<Category> findByNameContainingIgnoreCaseAndIsActiveTrue(String name);
    List<Category> findAllByOrderByDisplayOrderAsc(); 
    List<Category> findByIsActiveTrueAndDiscountGreaterThan(Double discount);
    @Query("SELECT c FROM Category c WHERE c.isActive = true AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Category> searchByNameOrDescription(@Param("keyword") String keyword);
}