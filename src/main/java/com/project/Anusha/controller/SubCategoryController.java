package com.project.Anusha.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Anusha.dto.SubCategoryRequest;
import com.project.Anusha.dto.SubCategoryResponse;
import com.project.Anusha.service.SubCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/subcategories")
@RequiredArgsConstructor
public class SubCategoryController {

    private final SubCategoryService service;
    private final ObjectMapper objectMapper;

    // ---------- CREATE (JSON only) ----------
    @PostMapping
    public ResponseEntity<SubCategoryResponse> create(@RequestBody SubCategoryRequest request) {
        SubCategoryResponse created = service.createSubCategory(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    // ---------- CREATE with image/video (multipart) ----------
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubCategoryResponse> createWithFiles(
            @RequestPart("subCategory") String subCategoryJson,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "video", required = false) MultipartFile video) throws IOException {

        SubCategoryRequest request = objectMapper.readValue(subCategoryJson, SubCategoryRequest.class);
        SubCategoryResponse created = service.createSubCategoryWithFiles(request, image, video);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    // ---------- UPDATE (JSON only) ----------
    @PutMapping("/{id}")
    public ResponseEntity<SubCategoryResponse> update(@PathVariable Long id,
                                                      @RequestBody SubCategoryRequest request) {
        SubCategoryResponse updated = service.updateSubCategory(id, request);
        return ResponseEntity.ok(updated);
    }

    // ---------- UPDATE with image/video (multipart) ----------
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubCategoryResponse> updateWithFiles(
            @PathVariable Long id,
            @RequestPart("subCategory") String subCategoryJson,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "video", required = false) MultipartFile video) throws IOException {

        SubCategoryRequest request = objectMapper.readValue(subCategoryJson, SubCategoryRequest.class);
        SubCategoryResponse updated = service.updateSubCategoryWithFiles(id, request, image, video);
        return ResponseEntity.ok(updated);
    }

    // ---------- READ ----------
    @GetMapping
    public List<SubCategoryResponse> getAll() {
        return service.getAllSubCategories();
    }

    @GetMapping("/{categoryId}")
    public List<SubCategoryResponse> getByCategory(@PathVariable Long categoryId) {
        return service.getByCategory(categoryId);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<SubCategoryResponse> getById(@PathVariable Long id) {
        SubCategoryResponse subCategory = service.getSubCategoryById(id);
        return ResponseEntity.ok(subCategory);
    }

    // ---------- DELETE ----------
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        service.softDeleteSubCategory(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        service.hardDeleteSubCategory(id);
        return ResponseEntity.noContent().build();
    }
}