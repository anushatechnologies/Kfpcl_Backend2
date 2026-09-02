package com.project.Anusha.service;

import com.project.Anusha.dto.CategoryRequest;
import com.project.Anusha.dto.CategoryResponse;
import com.project.Anusha.model.Category;
import com.project.Anusha.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;
    private final S3Service s3Service;   // <-- replaced CloudinaryService
    private final ProductService productService;

    public CategoryResponse createCategory(CategoryRequest request, MultipartFile imageFile) throws IOException {
        Category category = new Category();
        mapRequestToEntity(request, category);

        if (imageFile != null && !imageFile.isEmpty()) {
            category.setImageUrl(s3Service.uploadFile(imageFile, "categories"));   // <-- changed
        }

        category.setIsActive(true);
        Category saved = repository.save(category);
        return mapToResponse(saved);
    }

    public List<CategoryResponse> getAllCategories() {
        return repository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CategoryResponse updateCategory(Long id, CategoryRequest request, MultipartFile imageFile) throws IOException {
        Category existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        mapRequestToEntity(request, existing);

        if (imageFile != null && !imageFile.isEmpty()) {
            existing.setImageUrl(s3Service.uploadFile(imageFile, "categories"));   // <-- changed
        }

        Category updated = repository.save(existing);
        return mapToResponse(updated);
    }

    public List<CategoryResponse> getActiveCategories() {
        return repository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CategoryResponse getCategoryById(Long id) {
        Category category = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        return mapToResponse(category);
    }

    public List<CategoryResponse> searchCategories(String keyword) {
        return repository.searchByNameOrDescription(keyword)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<CategoryResponse> getCategoriesByMinDiscount(Double minDiscount) {
        return repository.findByIsActiveTrueAndDiscountGreaterThan(minDiscount)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void softDeleteCategory(Long id) {
        hardDeleteCategory(id);
    }

    public void hardDeleteCategory(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Category not found");
        }
        productService.deleteProductsByCategory(id);
        repository.deleteById(id);
    }

    private void mapRequestToEntity(CategoryRequest request, Category entity) {
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setIsActive(request.getIsActive());
        entity.setDisplayOrder(request.getDisplayOrder());
        entity.setDiscount(request.getDiscount());
        // Only overwrite imageUrl/videoUrl if the request explicitly provides one;
        // prevents clearing an existing S3 URL when the form doesn't resend it.
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            entity.setImageUrl(request.getImageUrl());
        }
        if (request.getVideoUrl() != null && !request.getVideoUrl().isBlank()) {
            entity.setVideoUrl(request.getVideoUrl());
        }
    }

    private CategoryResponse mapToResponse(Category entity) {
        CategoryResponse response = new CategoryResponse();
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
        return response;
    }
}
