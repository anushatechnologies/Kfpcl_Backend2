package com.project.Anusha.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Anusha.dto.ProductRequest;
import com.project.Anusha.dto.ProductResponse;
import com.project.Anusha.dto.ProductSummaryResponse;
import com.project.Anusha.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createProduct(
            @RequestParam("product") String productJson,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "video", required = false) MultipartFile video) throws IOException {
        ProductRequest request;
        try {
            request = parseProductRequest(productJson);
        } catch (JsonProcessingException e) {
            return badRequest("Invalid product JSON: " + e.getOriginalMessage());
        }

        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(productService.createProduct(request, image, video));
        } catch (IOException e) {
            return mediaUploadFailed(e);
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestParam("product") String productJson,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "video", required = false) MultipartFile video) {
        ProductRequest request;
        try {
            request = parseProductRequest(productJson);
        } catch (JsonProcessingException e) {
            return badRequest("Invalid product JSON: " + e.getOriginalMessage());
        }

        try {
            return ResponseEntity.ok(productService.updateProduct(id, request, image, video));
        } catch (IOException e) {
            return mediaUploadFailed(e);
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProductMetadata(
            @PathVariable Long id,
            @RequestBody ProductRequest request) {
        try {
            return ResponseEntity.ok(productService.updateProduct(id, request, null, null));
        } catch (IOException e) {
            return mediaUploadFailed(e);
        }
    }

    private ProductRequest parseProductRequest(String productJson) throws JsonProcessingException {
        return objectMapper.readValue(productJson, ProductRequest.class);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "error", "Bad Request",
                        "message", message
                ));
    }

    private ResponseEntity<Map<String, Object>> mediaUploadFailed(IOException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "status", HttpStatus.BAD_GATEWAY.value(),
                        "error", "Media Upload Failed",
                        "message", "Product details are valid, but image/video upload failed. Please try again."
                ));
    }

    @GetMapping
    public ResponseEntity<?> getAllProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive,
            @RequestParam(value = "full", defaultValue = "false") boolean full,
            @RequestParam(value = "page", defaultValue = "-1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size) {
        // If no filters and no explicit pagination (page=-1), return ALL products
        if (!full && page < 0 && categoryId == null && subCategoryId == null) {
            return ResponseEntity.ok(productService.getAllProducts(storeId));
        }

        // If full=true is requested, return all
        if (full) {
            return ResponseEntity.ok(productService.getAllProducts(storeId));
        }

        // Otherwise, return paginated summaries
        int actualPage = Math.max(page, 0);
        Page<ProductSummaryResponse> products = productService.getProductSummaries(
                categoryId, subCategoryId, storeId, includeInactive, actualPage, size);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/search")
    public List<ProductResponse> searchProducts(@RequestParam String keyword) {
        return productService.searchProducts(keyword);
    }

    /**
     * GET /api/products/suggestions?q=milk&limit=10
     * Zepto-style instant suggestions — fast, relevance-ranked, capped at 20.
     * Returns name-match first, then category/description matches.
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<ProductResponse>> getSearchSuggestions(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        if (q == null || q.trim().length() < 1) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(productService.getSearchSuggestions(q.trim(), limit));
    }

    /**
     * GET /api/products/search/paginated?q=milk&page=0&size=20
     * Full paginated search with Zepto-style relevance ordering.
     */
    @GetMapping("/search/paginated")
    public ResponseEntity<Page<ProductResponse>> searchPaginated(
            @RequestParam("q") String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size) {
        return ResponseEntity.ok(productService.searchProductsPaginated(q, page, size));
    }

    @GetMapping("/trending")
    public List<ProductResponse> getTrendingProducts() {
        return productService.getTrendingProducts();
    }

    @GetMapping("/bestseller")
    public List<ProductResponse> getBestSellerProducts() {
        return productService.getBestSellerProducts();
    }

    @GetMapping("/filter")
    public List<ProductResponse> filterProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Boolean trending,
            @RequestParam(required = false) String keyword) {
        return productService.filterProducts(categoryId, subCategoryId, storeId,
                minPrice, maxPrice, trending, keyword);
    }

    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

}
