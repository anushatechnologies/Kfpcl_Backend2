package com.project.Anusha.repository;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.Anusha.model.SubCategory;

import java.util.List;

public interface SubCategoryRepository extends JpaRepository<SubCategory, Long> {
    List<SubCategory> findByCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(Long categoryId);
    List<SubCategory> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);  
}