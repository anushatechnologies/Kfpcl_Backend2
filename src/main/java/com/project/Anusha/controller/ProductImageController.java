package com.project.Anusha.controller;

import com.project.Anusha.dto.ProductImageResponse;
import com.project.Anusha.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService productImageService;

    // POST /api/products/{productId}/images
    @PostMapping(value = "/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> uploadImage(
            @PathVariable Long productId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) Integer displayOrder
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productImageService.uploadImage(productId, image, displayOrder));
    }

    // GET /api/products/{productId}/images
    @GetMapping("/{productId}/images")
    public List<ProductImageResponse> getProductImages(@PathVariable Long productId) {
        return productImageService.getImagesByProductId(productId);
    }

    // PUT /api/products/images/{imageId}
    @PutMapping(value = "/images/{imageId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> updateImage(
            @PathVariable Long imageId,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(required = false) Integer displayOrder
    ) throws IOException {
        return ResponseEntity.ok(productImageService.updateImage(imageId, image, displayOrder));
    }

    // DELETE /api/products/images/{imageId}
    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long imageId) {
        productImageService.deleteImage(imageId);
        return ResponseEntity.noContent().build();
    }
}
