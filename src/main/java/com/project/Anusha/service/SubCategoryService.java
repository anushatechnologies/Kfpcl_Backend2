package com.project.Anusha.service;

import com.project.Anusha.dto.SubCategoryRequest;
import com.project.Anusha.dto.SubCategoryResponse;
import com.project.Anusha.model.Category;
import com.project.Anusha.model.SubCategory;
import com.project.Anusha.repository.CategoryRepository;
import com.project.Anusha.repository.SubCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubCategoryService {

    private final SubCategoryRepository repository;
    private final CategoryRepository categoryRepository;
    private final S3Service s3Service;
    private final ProductService productService;

    // ---------- CREATE without files ----------
    public SubCategoryResponse createSubCategory(SubCategoryRequest request) {
        SubCategory subCategory = new SubCategory();
        mapRequestToEntity(request, subCategory);
        // No file upload – image/video URLs come directly from request if provided
        SubCategory saved = repository.save(subCategory);
        return mapToResponse(saved);
    }

    // ---------- CREATE with optional image/video ----------
    public SubCategoryResponse createSubCategoryWithFiles(SubCategoryRequest request,
                                                          MultipartFile image,
                                                          MultipartFile video) throws IOException {
        SubCategory subCategory = new SubCategory();
        mapRequestToEntity(request, subCategory);

        if (image != null && !image.isEmpty()) {
            subCategory.setImageUrl(s3Service.uploadFile(image, "subcategories"));
        }
        if (video != null && !video.isEmpty()) {
            subCategory.setVideoUrl(s3Service.uploadFile(video, "subcategories"));
        }

        SubCategory saved = repository.save(subCategory);
        return mapToResponse(saved);
    }

    // ---------- UPDATE without files ----------
    public SubCategoryResponse updateSubCategory(Long id, SubCategoryRequest request) {
        SubCategory existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("SubCategory not found with id: " + id));
        mapRequestToEntity(request, existing);
        // No file upload – keep existing image/video URLs or overwrite with request values
        SubCategory updated = repository.save(existing);
        return mapToResponse(updated);
    }

    // ---------- UPDATE with optional image/video ----------
    public SubCategoryResponse updateSubCategoryWithFiles(Long id,
                                                          SubCategoryRequest request,
                                                          MultipartFile image,
                                                          MultipartFile video) throws IOException {
        SubCategory existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("SubCategory not found with id: " + id));
        mapRequestToEntity(request, existing);

        if (image != null && !image.isEmpty()) {
            existing.setImageUrl(s3Service.uploadFile(image, "subcategories"));
        }
        if (video != null && !video.isEmpty()) {
            existing.setVideoUrl(s3Service.uploadFile(video, "subcategories"));
        }

        SubCategory updated = repository.save(existing);
        return mapToResponse(updated);
    }

    // ---------- READ ----------
    public SubCategoryResponse getSubCategoryById(Long id) {
        SubCategory subCategory = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("SubCategory not found with id: " + id));
        return mapToResponse(subCategory);
    }

    public List<SubCategoryResponse> getByCategory(Long categoryId) {
        return repository.findByCategoryIdOrderByDisplayOrderAsc(categoryId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<SubCategoryResponse> getAllSubCategories() {
        return repository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ---------- DELETE ----------
    public void softDeleteSubCategory(Long id) {
        hardDeleteSubCategory(id);
    }

    public void hardDeleteSubCategory(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("SubCategory not found with id: " + id);
        }
        productService.deleteProductsBySubCategory(id);
        repository.deleteById(id);
    }

    // ---------- MAPPING HELPERS ----------
    private void mapRequestToEntity(SubCategoryRequest request, SubCategory entity) {
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setIsActive(request.getIsActive());
        entity.setDisplayOrder(request.getDisplayOrder());
        entity.setDiscount(request.getDiscount());
        // Only overwrite imageUrl/videoUrl if request explicitly provides a non-blank value.
        // This prevents wiping the existing S3 URL when the form doesn't resend it.
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            entity.setImageUrl(request.getImageUrl());
        }
        if (request.getVideoUrl() != null && !request.getVideoUrl().isBlank()) {
            entity.setVideoUrl(request.getVideoUrl());
        }

        if (request.getCategoryId() != null && request.getCategoryId() > 0) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + request.getCategoryId()));
            entity.setCategory(category);
        }
    }

    private SubCategoryResponse mapToResponse(SubCategory entity) {
        SubCategoryResponse response = new SubCategoryResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setIsActive(entity.getIsActive());
        response.setDisplayOrder(entity.getDisplayOrder());
        response.setDiscount(entity.getDiscount());
        response.setImageUrl(entity.getImageUrl());
        response.setVideoUrl(entity.getVideoUrl());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getCategory() != null) {
            response.setCategoryId(entity.getCategory().getId());
            response.setCategoryName(entity.getCategory().getName());
        }

        return response;
    }
}
