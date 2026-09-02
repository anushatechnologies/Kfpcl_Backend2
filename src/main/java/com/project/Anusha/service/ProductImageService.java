package com.project.Anusha.service;

import com.project.Anusha.dto.ProductImageResponse;
import com.project.Anusha.model.Product;
import com.project.Anusha.model.ProductImage;
import com.project.Anusha.repository.ProductImageRepository;
import com.project.Anusha.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final S3Service s3Service;

    @Transactional
    public ProductImageResponse uploadImage(Long productId, MultipartFile imageFile, Integer displayOrder) throws IOException {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }

        String imageUrl = s3Service.uploadFile(imageFile, "products/gallery");

        ProductImage productImage = new ProductImage();
        productImage.setProduct(product);
        productImage.setImageUrl(imageUrl);
        productImage.setDisplayOrder(displayOrder != null ? displayOrder : 0);

        ProductImage saved = productImageRepository.save(productImage);
        return mapToResponse(saved);
    }

    public List<ProductImageResponse> getImagesByProductId(Long productId) {
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductImageResponse updateImage(Long imageId, MultipartFile newImageFile, Integer displayOrder) throws IOException {
        ProductImage existing = productImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("ProductImage not found"));
        String previousImageUrl = existing.getImageUrl();

        if (newImageFile != null && !newImageFile.isEmpty()) {
            String imageUrl = s3Service.uploadFile(newImageFile, "products/gallery");
            existing.setImageUrl(imageUrl);
        }

        if (displayOrder != null) {
            existing.setDisplayOrder(displayOrder);
        }

        ProductImage updated = productImageRepository.save(existing);
        if (previousImageUrl != null && !previousImageUrl.equals(updated.getImageUrl())) {
            try {
                s3Service.deleteFileByUrl(previousImageUrl);
            } catch (RuntimeException ex) {
                log.warn("Failed to delete replaced product image from S3: {}", previousImageUrl, ex);
            }
        }
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteImage(Long imageId) {
        ProductImage existing = productImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("ProductImage not found"));
        s3Service.deleteFileByUrl(existing.getImageUrl());
        productImageRepository.delete(existing);
    }

    public ProductImageResponse mapToResponse(ProductImage entity) {
        ProductImageResponse response = new ProductImageResponse();
        response.setId(entity.getId());
        response.setImageUrl(entity.getImageUrl());
        response.setDisplayOrder(entity.getDisplayOrder());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
