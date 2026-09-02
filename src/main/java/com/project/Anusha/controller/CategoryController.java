package com.project.Anusha.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Anusha.dto.CategoryRequest;
import com.project.Anusha.dto.CategoryResponse;
import com.project.Anusha.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@CrossOrigin("*")
public class CategoryController {

    private final CategoryService service;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CategoryResponse> create(
            @RequestPart("category") String categoryJson,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {

        CategoryRequest request = objectMapper.readValue(categoryJson, CategoryRequest.class);
        CategoryResponse created = service.createCategory(request, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Long id,
            @RequestPart("category") String categoryJson,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {

        CategoryRequest request = objectMapper.readValue(categoryJson, CategoryRequest.class);
        CategoryResponse updated = service.updateCategory(id, request, image);
        return ResponseEntity.ok(updated);
    }

    @GetMapping
    public List<CategoryResponse> getAll() {
        return service.getAllCategories();
    }

    @GetMapping("/{id}")
    public CategoryResponse getById(@PathVariable Long id) {
        return service.getCategoryById(id);
    }

    @GetMapping("/search")
    public List<CategoryResponse> search(@RequestParam String keyword) {
        return service.searchCategories(keyword);
    }

    @GetMapping("/filter/discount")
    public List<CategoryResponse> filterByDiscount(@RequestParam Double minDiscount) {
        return service.getCategoriesByMinDiscount(minDiscount);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        service.softDeleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        service.hardDeleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}