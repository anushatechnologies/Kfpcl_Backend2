package com.project.Anusha.controller;

import com.project.Anusha.dto.CategoryDetailResponse;
import com.project.Anusha.model.Category;
import com.project.Anusha.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllActiveCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDetailResponse> getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryDetails(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
