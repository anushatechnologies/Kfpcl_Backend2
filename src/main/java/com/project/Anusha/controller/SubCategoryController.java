package com.project.Anusha.controller;

import com.project.Anusha.model.Subcategory;
import com.project.Anusha.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subcategories")
@CrossOrigin(origins = "*")
public class SubcategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/{categoryId}")
    public ResponseEntity<List<Subcategory>> getSubcategoriesByCategoryId(@PathVariable Long categoryId) {
        return ResponseEntity.ok(categoryService.getSubcategoriesByCategoryId(categoryId));
    }
}
