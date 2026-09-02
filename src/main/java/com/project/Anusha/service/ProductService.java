package com.project.Anusha.service;

import com.project.Anusha.dto.ProductRequest;
import com.project.Anusha.dto.ProductResponse;
import com.project.Anusha.dto.ProductSummaryResponse;
import com.project.Anusha.dto.VariantRequest;
import com.project.Anusha.dto.VariantResponse;
import com.project.Anusha.exception.ImageRequiredException;
import com.project.Anusha.exception.ResourceNotFoundException;
import com.project.Anusha.model.*;
import com.project.Anusha.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final StoreRepository storeRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final WishlistRepository wishlistRepository;
    private final ProductRatingRepository productRatingRepository;
    private final S3Service s3Service;

    // ================= CREATE =================
    public ProductResponse createProduct(ProductRequest request, MultipartFile imageFile, MultipartFile videoFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new ImageRequiredException("Product image is required");
        }
        if (request.getVariants() == null || request.getVariants().isEmpty()) {
            throw new IllegalArgumentException("At least one product variant is required");
        }

        Product product = new Product();
        applyRequestToProduct(product, request, false);

        // Upload main product image
        String imageUrl = s3Service.uploadFile(imageFile, "products");
        product.setImageUrl(imageUrl);
        if (videoFile != null && !videoFile.isEmpty()) {
            product.setVideoUrl(s3Service.uploadFile(videoFile, "products"));
        }

        // Create variants
        List<Variant> variants = new ArrayList<>();
        for (VariantRequest vr : request.getVariants()) {
            Variant variant = new Variant();
            applyRequestToVariant(variant, vr, false);
            variant.setProduct(product);
            variants.add(variant);
        }
        product.setVariants(variants);

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    // ================= UPDATE =================
    public ProductResponse updateProduct(Long id, ProductRequest request, MultipartFile imageFile, MultipartFile videoFile) throws IOException {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        String previousImageUrl = product.getImageUrl();
        String previousVideoUrl = product.getVideoUrl();

        // Patch-style: only overwrite fields explicitly provided in the request.
        applyRequestToProduct(product, request, true);

        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = s3Service.uploadFile(imageFile, "products");
            product.setImageUrl(imageUrl);
        }
        if (videoFile != null && !videoFile.isEmpty()) {
            product.setVideoUrl(s3Service.uploadFile(videoFile, "products"));
        }

        // Smart variant update to avoid FK constraint issues (DataIntegrityViolationException)
        if (request.getVariants() != null) {
            if (product.getVariants() == null) {
                product.setVariants(new java.util.ArrayList<>());
            }

            java.util.Map<Long, Variant> existingMap = product.getVariants().stream()
                    .filter(v -> v.getId() != null)
                    .collect(java.util.stream.Collectors.toMap(
                        Variant::getId,
                        v -> v,
                        (existing, replacement) -> existing
                    ));

            for (VariantRequest vr : request.getVariants()) {
                if (vr.getId() != null && existingMap.containsKey(vr.getId())) {
                    Variant existing = existingMap.get(vr.getId());
                    applyRequestToVariant(existing, vr, true);
                    existingMap.remove(vr.getId());
                } else {
                    Variant existing = findMatchingExistingVariant(existingMap, vr);
                    if (existing != null) {
                        applyRequestToVariant(existing, vr, true);
                        existingMap.remove(existing.getId());
                    } else {
                        Variant nouveau = new Variant();
                        applyRequestToVariant(nouveau, vr, false);
                        nouveau.setProduct(product);
                        product.getVariants().add(nouveau);
                    }
                }
            }

            // Variants not in the request are soft-deactivated (NOT removed) so
            // existing CartItem/OrderItem FK references don't blow up the save.
            for (Variant unused : existingMap.values()) {
                unused.setIsActive(false);
            }

            product.getVariants().sort(java.util.Comparator
                    .comparing((Variant variant) -> variant.getDisplayOrder(), java.util.Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(Variant::getId, java.util.Comparator.nullsLast(Long::compareTo)));
        }

        Product updated;
        try {
            updated = productRepository.save(product);
        } catch (RuntimeException ex) {
            log.error("Failed to update product id={}: {}", id, ex.getMessage(), ex);
            throw ex;
        }
        deleteReplacedAsset(previousImageUrl, updated.getImageUrl(), "product image");
        deleteReplacedAsset(previousVideoUrl, updated.getVideoUrl(), "product video");
        return mapToResponse(updated);
    }

    // ================= GET ALL =================
    public List<ProductResponse> getAllProducts(Long storeId) {
        List<Product> products;
        if (storeId != null) {
            products = productRepository.findByStoreIdAndDeletedFalse(storeId);
        } else {
            products = productRepository.findByDeletedFalse();
        }
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public Page<ProductSummaryResponse> getProductSummaries(
            Long categoryId,
            Long subCategoryId,
            Long storeId,
            boolean includeInactive,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return productRepository.findProductPage(categoryId, subCategoryId, storeId, includeInactive, pageable)
                .map(this::mapToSummaryResponse);
    }

    // ================= GET BY ID =================
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapToResponse(product);
    }

    // ================= DELETE =================
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        hardDeleteProduct(product);
    }

    public void deleteProductsBySubCategory(Long subCategoryId) {
        List<Product> products = productRepository.findBySubCategoryId(subCategoryId);
        for (Product product : products) {
            hardDeleteProduct(product);
        }
    }

    public void deleteProductsByCategory(Long categoryId) {
        List<Product> products = productRepository.findByCategoryOrSubCategoryCategory(categoryId);
        for (Product product : products) {
            hardDeleteProduct(product);
        }
    }

    private void hardDeleteProduct(Product product) {
        Long productId = product.getId();
        if (product.getVariants() != null) {
            for (Variant variant : product.getVariants()) {
                if (variant.getId() != null) {
                    cartItemRepository.deleteByVariantId(variant.getId());
                }
            }
        }
        orderItemRepository.deleteByProductId(productId);
        wishlistRepository.deleteByProductId(productId);
        productRatingRepository.deleteByProductId(productId);
        productRepository.delete(product);
    }

    // ================= SEARCH =================
    public List<ProductResponse> searchProducts(String keyword) {
        List<Product> products = productRepository.searchByNameOnly(keyword);
        return products.stream()
                .filter(this::isVisibleForCustomer)
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Zepto-style instant suggestions — returns up to `limit` results ranked by relevance.
     * Fast: no pagination overhead, capped at 10 by default.
     */
    public List<ProductResponse> getSearchSuggestions(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String kw = keyword.trim().toLowerCase();
        String compactKw = kw.replaceAll("\\s+", "");
        Pageable pageable = PageRequest.of(0, Math.min(limit, 20));
        return productRepository.searchSuggestions(kw, compactKw, pageable)
                .getContent()
                .stream()
                .filter(this::isVisibleForCustomer)
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Full paginated search — Zepto-style relevance ordering, returns a Page.
     */
    public Page<ProductResponse> searchProductsPaginated(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new PageImpl<>(List.of());
        }
        String kw = keyword.trim().toLowerCase();
        String compactKw = kw.replaceAll("\\s+", "");
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<Product> productPage = productRepository.searchPaginated(kw, compactKw, pageable);
        List<ProductResponse> content = productPage.getContent()
                .stream()
                .filter(this::isVisibleForCustomer)
                .map(this::mapToResponse)
                .toList();
        return new PageImpl<>(content, pageable, productPage.getTotalElements());
    }

    // ================= TRENDING =================
    public List<ProductResponse> getTrendingProducts() {
        return productRepository.findByIsTrendingTrueAndIsActiveTrue()
                .stream()
                .filter(this::isVisibleForCustomer)
                .map(this::mapToResponse)
                .toList();
    }

    // ================= BEST SELLER =================
    public List<ProductResponse> getBestSellerProducts() {
        return productRepository.findByBestSellerTrueAndIsActiveTrue()
                .stream()
                .filter(this::isVisibleForCustomer)
                .map(this::mapToResponse)
                .toList();
    }

    // ================= FILTER =================
    public List<ProductResponse> filterProducts(
            Long categoryId,
            Long subCategoryId,
            Long storeId,
            Double minPrice,
            Double maxPrice,
            Boolean trending,
            String keyword
    ) {
        List<Product> products = productRepository.filterProducts(
                categoryId, subCategoryId, storeId, minPrice, maxPrice, trending, keyword
        );
        return products.stream()
                .filter(this::isVisibleForCustomer)
                .map(this::mapToResponse)
                .toList();
    }

    // ================= HELPER METHODS =================
    /**
     * Apply a {@link ProductRequest} to a {@link Product}. When {@code patch} is true,
     * fields that are null in the request are left untouched on the entity (so the
     * admin form can send a subset without clobbering flags it doesn't know about,
     * such as flashSale*, publishAt, isDraft). On create, defaults kick in.
     */
    private void applyRequestToProduct(Product product, ProductRequest request, boolean patch) {
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());

        if (request.getIsActive() != null) {
            product.setIsActive(request.getIsActive());
        } else if (!patch) {
            product.setIsActive(true);
        }
        if (request.getIsTrending() != null) {
            product.setIsTrending(request.getIsTrending());
        } else if (!patch) {
            product.setIsTrending(false);
        }
        if (request.getBestSeller() != null) {
            product.setBestSeller(request.getBestSeller());
        } else if (!patch) {
            product.setBestSeller(false);
        }
        if (request.getIsDraft() != null) {
            product.setIsDraft(request.getIsDraft());
        } else if (!patch) {
            product.setIsDraft(false);
        }
        if (request.getDisplayOrder() != null) {
            product.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getPublishAt() != null) product.setPublishAt(request.getPublishAt());
        if (request.getUnpublishAt() != null) product.setUnpublishAt(request.getUnpublishAt());
        if (request.getFlashSaleEnabled() != null) {
            product.setFlashSaleEnabled(request.getFlashSaleEnabled());
        } else if (!patch) {
            product.setFlashSaleEnabled(false);
        }
        if (request.getFlashSaleStartAt() != null) product.setFlashSaleStartAt(request.getFlashSaleStartAt());
        if (request.getFlashSaleEndAt() != null) product.setFlashSaleEndAt(request.getFlashSaleEndAt());
        if (request.getFlashSalePrice() != null) product.setFlashSalePrice(request.getFlashSalePrice());
        if (request.getHsnCode() != null) {
            String trimmed = request.getHsnCode().trim();
            product.setHsnCode(trimmed.isEmpty() ? null : trimmed);
        }
        if (request.getGstRate() != null) {
            product.setGstRate(request.getGstRate());
        } else if (!patch) {
            product.setGstRate(java.math.BigDecimal.ZERO);
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }

        if (request.getSubCategoryId() != null) {
            SubCategory subCategory = subCategoryRepository.findById(request.getSubCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("SubCategory not found with id: " + request.getSubCategoryId()));
            product.setSubCategory(subCategory);
        }

        if (request.getStoreId() != null) {
            Store store = storeRepository.findById(request.getStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + request.getStoreId()));
            product.setStore(store);
        }
    }

    private void applyRequestToVariant(Variant variant, VariantRequest request, boolean patch) {
        if (request.getName() != null) variant.setName(request.getName());
        if (request.getSku() != null) variant.setSku(request.getSku());
        if (request.getPrice() != null) variant.setPrice(request.getPrice());
        if (request.getDiscountPrice() != null) variant.setDiscountPrice(request.getDiscountPrice());
        if (request.getStock() != null) variant.setStock(request.getStock());
        if (request.getIsActive() != null) {
            variant.setIsActive(request.getIsActive());
        } else if (!patch) {
            variant.setIsActive(true);
        }
        if (request.getDisplayOrder() != null) {
            variant.setDisplayOrder(request.getDisplayOrder());
        }
    }

    private Variant findMatchingExistingVariant(java.util.Map<Long, Variant> existingMap, VariantRequest request) {
        String requestedSku = normalizeVariantKey(request.getSku());
        if (requestedSku != null) {
            for (Variant variant : existingMap.values()) {
                if (requestedSku.equals(normalizeVariantKey(variant.getSku()))) {
                    return variant;
                }
            }
        }

        String requestedName = normalizeVariantKey(request.getName());
        if (requestedName != null) {
            for (Variant variant : existingMap.values()) {
                if (requestedName.equals(normalizeVariantKey(variant.getName()))) {
                    return variant;
                }
            }
        }

        return null;
    }

    private String normalizeVariantKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public ProductResponse mapToResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setIsActive(product.getIsActive());
        response.setIsTrending(product.getIsTrending());
        response.setBestSeller(product.getBestSeller());
        response.setIsDraft(product.getIsDraft());
        response.setDisplayOrder(product.getDisplayOrder());
        response.setPublishAt(product.getPublishAt());
        response.setUnpublishAt(product.getUnpublishAt());
        response.setFlashSaleEnabled(product.getFlashSaleEnabled());
        response.setFlashSaleStartAt(product.getFlashSaleStartAt());
        response.setFlashSaleEndAt(product.getFlashSaleEndAt());
        response.setFlashSalePrice(product.getFlashSalePrice());
        response.setHsnCode(product.getHsnCode());
        response.setGstRate(product.getGstRate());
        response.setFlashSaleActive(isFlashSaleActive(product));
        String primaryImageUrl = product.getImageUrl();
        if ((primaryImageUrl == null || primaryImageUrl.isBlank()) && product.getImages() != null) {
            primaryImageUrl = product.getImages().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(ProductImage::getImageUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        response.setImageUrl(primaryImageUrl);
        response.setVideoUrl(product.getVideoUrl());

        if (product.getCategory() != null) {
            response.setCategoryId(product.getCategory().getId());
            response.setCategoryName(product.getCategory().getName());
        }
        if (product.getSubCategory() != null) {
            response.setSubCategoryId(product.getSubCategory().getId());
            response.setSubCategoryName(product.getSubCategory().getName());
        }
        if (product.getStore() != null) {
            response.setStoreId(product.getStore().getId());
            response.setStoreName(product.getStore().getName());
        }

        // Map images
        if (product.getImages() != null) {
            List<com.project.Anusha.dto.ProductImageResponse> imageResponses = product.getImages().stream()
                    .map(this::mapImageToResponse)
                    .collect(Collectors.toList());
            response.setImages(imageResponses);
        }

        // Map variants
        List<VariantResponse> variantResponses = product.getVariants().stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive())) // only return active variants, safe against null
                .map(this::mapVariantToResponse)
                .collect(Collectors.toList());
        response.setVariants(variantResponses);

        // Compute price range from active variants (using discount price if present)
        // Filter out nulls to prevent NPE in min/max comparison
        Double min = variantResponses.stream()
                .map(v -> v.getDiscountPrice() != null ? v.getDiscountPrice() : v.getPrice())
                .filter(java.util.Objects::nonNull)
                .min(Double::compareTo).orElse(null);
        Double max = variantResponses.stream()
                .map(v -> v.getDiscountPrice() != null ? v.getDiscountPrice() : v.getPrice())
                .filter(java.util.Objects::nonNull)
                .max(Double::compareTo).orElse(null);
        response.setMinPrice(min);
        response.setMaxPrice(max);

        return response;
    }

    public ProductSummaryResponse mapToSummaryResponse(Product product) {
        ProductSummaryResponse response = new ProductSummaryResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setImageUrl(resolvePrimaryImageUrl(product));
        response.setIsTrending(product.getIsTrending());
        response.setBestSeller(product.getBestSeller());
        response.setFlashSaleActive(isFlashSaleActive(product));
        response.setFlashSalePrice(product.getFlashSalePrice());
        response.setHsnCode(product.getHsnCode());
        response.setGstRate(product.getGstRate());

        if (product.getCategory() != null) {
            response.setCategoryId(product.getCategory().getId());
            response.setCategoryName(product.getCategory().getName());
        }
        if (product.getSubCategory() != null) {
            response.setSubCategoryId(product.getSubCategory().getId());
            response.setSubCategoryName(product.getSubCategory().getName());
        }
        if (product.getStore() != null) {
            response.setStoreId(product.getStore().getId());
            response.setStoreName(product.getStore().getName());
        }

        List<Variant> activeVariants = product.getVariants() == null
                ? List.of()
                : product.getVariants().stream()
                        .filter(variant -> Boolean.TRUE.equals(variant.getIsActive()))
                        .toList();

        Variant firstVariant = activeVariants.stream()
                .sorted(java.util.Comparator
                        .comparing(Variant::getDisplayOrder, java.util.Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Variant::getId, java.util.Comparator.nullsLast(Long::compareTo)))
                .findFirst()
                .orElse(null);
        if (firstVariant != null) {
            response.setPrice(firstVariant.getPrice());
            response.setDiscountPrice(firstVariant.getDiscountPrice());
            response.setStock(firstVariant.getStock());
        }

        Double min = activeVariants.stream()
                .map(v -> v.getDiscountPrice() != null ? v.getDiscountPrice() : v.getPrice())
                .filter(java.util.Objects::nonNull)
                .min(Double::compareTo).orElse(null);
        Double max = activeVariants.stream()
                .map(v -> v.getDiscountPrice() != null ? v.getDiscountPrice() : v.getPrice())
                .filter(java.util.Objects::nonNull)
                .max(Double::compareTo).orElse(null);
        response.setMinPrice(min);
        response.setMaxPrice(max);

        return response;
    }

    private String resolvePrimaryImageUrl(Product product) {
        String primaryImageUrl = product.getImageUrl();
        if ((primaryImageUrl == null || primaryImageUrl.isBlank()) && product.getImages() != null) {
            primaryImageUrl = product.getImages().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(ProductImage::getImageUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return primaryImageUrl;
    }

    private VariantResponse mapVariantToResponse(Variant variant) {
        VariantResponse response = new VariantResponse();
        response.setId(variant.getId());
        response.setName(variant.getName());
        response.setSku(variant.getSku());
        response.setPrice(variant.getPrice());
        response.setDiscountPrice(variant.getDiscountPrice());
        response.setStock(variant.getStock());
        response.setIsActive(variant.getIsActive());
        response.setDisplayOrder(variant.getDisplayOrder());
        return response;
    }

    private com.project.Anusha.dto.ProductImageResponse mapImageToResponse(ProductImage image) {
        com.project.Anusha.dto.ProductImageResponse response = new com.project.Anusha.dto.ProductImageResponse();
        response.setId(image.getId());
        response.setImageUrl(image.getImageUrl());
        response.setDisplayOrder(image.getDisplayOrder());
        response.setCreatedAt(image.getCreatedAt());
        return response;
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 * * * * *")
    public void syncProductLifecycleStates() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int deactivated = productRepository.deactivateExpiredOrDraftProducts(now);
            int activated = productRepository.activatePublishedProducts(now);
            if (deactivated > 0 || activated > 0) {
                log.info("Synced product lifecycle states: deactivated={}, activated={}", deactivated, activated);
            }
        } catch (Exception e) {
            log.error("Failed to sync product lifecycle states: {}", e.getMessage(), e);
        }
    }

    private boolean computeLifecycleActive(Product product, LocalDateTime now) {
        if (Boolean.TRUE.equals(product.getDeleted()) || Boolean.TRUE.equals(product.getIsDraft())) {
            return false;
        }
        if (product.getPublishAt() != null && now.isBefore(product.getPublishAt())) {
            return false;
        }
        if (product.getUnpublishAt() != null && now.isAfter(product.getUnpublishAt())) {
            return false;
        }
        return Boolean.TRUE.equals(product.getIsActive()) || product.getPublishAt() != null;
    }

    private boolean isVisibleForCustomer(Product product) {
        return !Boolean.TRUE.equals(product.getDeleted())
                && !Boolean.TRUE.equals(product.getIsDraft())
                && Boolean.TRUE.equals(product.getIsActive());
    }

    private boolean isFlashSaleActive(Product product) {
        if (!Boolean.TRUE.equals(product.getFlashSaleEnabled())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean started = product.getFlashSaleStartAt() == null || !now.isBefore(product.getFlashSaleStartAt());
        boolean notEnded = product.getFlashSaleEndAt() == null || !now.isAfter(product.getFlashSaleEndAt());
        return started && notEnded;
    }

    private void deleteReplacedAsset(String previousUrl, String currentUrl, String assetType) {
        if (previousUrl == null || previousUrl.isBlank() || previousUrl.equals(currentUrl)) {
            return;
        }

        try {
            s3Service.deleteFileByUrl(previousUrl);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete replaced {} from S3: {}", assetType, previousUrl, ex);
        }
    }
}
