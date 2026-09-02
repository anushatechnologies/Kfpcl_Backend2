package com.project.Anusha.service;

import com.project.Anusha.dto.FreeItemOfferRequest;
import com.project.Anusha.dto.FreeItemOfferResponse;
import com.project.Anusha.model.Cart;
import com.project.Anusha.model.FreeItemOffer;
import com.project.Anusha.model.Product;
import com.project.Anusha.model.Variant;
import com.project.Anusha.repository.FreeItemOfferRepository;
import com.project.Anusha.repository.VariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FreeItemOfferService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final FreeItemOfferRepository freeItemOfferRepository;
    private final VariantRepository variantRepository;

    @Transactional(readOnly = true)
    public List<FreeItemOfferResponse> getAllOffers() {
        return freeItemOfferRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FreeItemOfferResponse> getActiveOffers() {
        return freeItemOfferRepository.findByActiveTrueOrderByUpdatedAtDesc().stream()
                .filter(this::isCurrentlyActive)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FreeItemOfferResponse getOffer(Long id) {
        return toResponse(findOffer(id));
    }

    public FreeItemOfferResponse createOffer(FreeItemOfferRequest request) {
        FreeItemOffer offer = new FreeItemOffer();
        applyRequest(offer, request);
        return toResponse(freeItemOfferRepository.save(offer));
    }

    public FreeItemOfferResponse updateOffer(Long id, FreeItemOfferRequest request) {
        FreeItemOffer offer = findOffer(id);
        applyRequest(offer, request);
        return toResponse(freeItemOfferRepository.save(offer));
    }

    public void deleteOffer(Long id) {
        freeItemOfferRepository.delete(findOffer(id));
    }

    @Transactional(readOnly = true)
    public List<AppliedFreeItem> getApplicableFreeItems(Cart cart) {
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> quantityByVariant = cart.getItems().stream()
                .filter(item -> item.getVariant() != null && item.getVariant().getId() != null)
                .collect(Collectors.toMap(
                        item -> item.getVariant().getId(),
                        item -> item.getQuantity() == null ? 0 : item.getQuantity(),
                        Integer::sum));

        Map<Long, Integer> quantityByProduct = cart.getItems().stream()
                .filter(item -> item.getVariant() != null
                        && item.getVariant().getProduct() != null
                        && item.getVariant().getProduct().getId() != null)
                .collect(Collectors.toMap(
                        item -> item.getVariant().getProduct().getId(),
                        item -> item.getQuantity() == null ? 0 : item.getQuantity(),
                        Integer::sum));

        return freeItemOfferRepository.findByActiveTrueOrderByUpdatedAtDesc().stream()
                .filter(this::isCurrentlyActive)
                .map(offer -> toAppliedFreeItem(offer, quantityByVariant, quantityByProduct))
                .filter(Objects::nonNull)
                .toList();
    }

    private AppliedFreeItem toAppliedFreeItem(
            FreeItemOffer offer,
            Map<Long, Integer> quantityByVariant,
            Map<Long, Integer> quantityByProduct
    ) {
        Variant qualifyingVariant = offer.getQualifyingVariant();
        Variant freeVariant = offer.getFreeVariant();
        if (qualifyingVariant == null || qualifyingVariant.getId() == null || freeVariant == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(freeVariant.getIsActive())) {
            return null;
        }

        int qualifyingQuantity = positiveOrDefault(offer.getQualifyingQuantity(), 1);
        int freeQuantity = positiveOrDefault(offer.getFreeQuantity(), 1);

        int cartQuantity;
        if (offer.isQualifyingByProduct()) {
            Product product = qualifyingVariant.getProduct();
            if (product == null || product.getId() == null) {
                return null;
            }
            cartQuantity = quantityByProduct.getOrDefault(product.getId(), 0);
        } else {
            cartQuantity = quantityByVariant.getOrDefault(qualifyingVariant.getId(), 0);
        }

        int offerMultiplier = cartQuantity / qualifyingQuantity;
        if (offerMultiplier <= 0) {
            return null;
        }

        int appliedFreeQuantity = offerMultiplier * freeQuantity;
        if (freeVariant.getStock() == null || freeVariant.getStock() < appliedFreeQuantity) {
            return null;
        }

        return new AppliedFreeItem(offer, freeVariant, appliedFreeQuantity);
    }

    private boolean isCurrentlyActive(FreeItemOffer offer) {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        return (offer.getStartDate() == null || !now.isBefore(offer.getStartDate()))
                && (offer.getExpiryDate() == null || !now.isAfter(offer.getExpiryDate()));
    }

    private void applyRequest(FreeItemOffer offer, FreeItemOfferRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Offer details are required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Offer name is required");
        }
        if (request.getQualifyingVariantId() == null) {
            throw new IllegalArgumentException("Buy variant is required");
        }
        if (request.getFreeVariantId() == null) {
            throw new IllegalArgumentException("Free variant is required");
        }
        if (Objects.equals(request.getQualifyingVariantId(), request.getFreeVariantId())) {
            throw new IllegalArgumentException("Buy variant and free variant must be different");
        }
        validateDates(request.getStartDate(), request.getExpiryDate());

        offer.setName(request.getName().trim());
        offer.setDescription(request.getDescription());
        offer.setQualifyingVariant(findVariant(request.getQualifyingVariantId(), "Buy variant not found"));
        offer.setQualifyingQuantity(positiveOrDefault(request.getQualifyingQuantity(), 1));
        offer.setQualifyingByProduct(Boolean.TRUE.equals(request.getQualifyingByProduct()));
        offer.setFreeVariant(findVariant(request.getFreeVariantId(), "Free variant not found"));
        offer.setFreeQuantity(positiveOrDefault(request.getFreeQuantity(), 1));
        offer.setStartDate(request.getStartDate());
        offer.setExpiryDate(request.getExpiryDate());
        offer.setActive(request.getActive() == null || request.getActive());
    }

    private FreeItemOffer findOffer(Long id) {
        return freeItemOfferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Free item offer not found"));
    }

    private Variant findVariant(Long id, String message) {
        return variantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(message + ": " + id));
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private void validateDates(LocalDateTime startDate, LocalDateTime expiryDate) {
        if (startDate != null && expiryDate != null && expiryDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Expiry date must be after start date");
        }
    }

    public FreeItemOfferResponse toResponse(FreeItemOffer offer) {
        FreeItemOfferResponse response = new FreeItemOfferResponse();
        response.setId(offer.getId());
        response.setName(offer.getName());
        response.setDescription(offer.getDescription());
        response.setQualifyingQuantity(offer.getQualifyingQuantity());
        response.setQualifyingByProduct(offer.isQualifyingByProduct());
        response.setFreeQuantity(offer.getFreeQuantity());
        response.setStartDate(offer.getStartDate());
        response.setExpiryDate(offer.getExpiryDate());
        response.setActive(offer.isActive());
        response.setCreatedAt(offer.getCreatedAt());
        response.setUpdatedAt(offer.getUpdatedAt());
        populateVariant(response, offer.getQualifyingVariant(), true);
        populateVariant(response, offer.getFreeVariant(), false);
        return response;
    }

    private void populateVariant(FreeItemOfferResponse response, Variant variant, boolean qualifying) {
        if (variant == null) {
            return;
        }
        Product product = variant.getProduct();
        if (qualifying) {
            response.setQualifyingVariantId(variant.getId());
            response.setQualifyingVariantName(variant.getName());
            response.setQualifyingVariantSku(variant.getSku());
            if (product != null) {
                response.setQualifyingProductId(product.getId());
                response.setQualifyingProductName(product.getName());
                response.setQualifyingProductImage(product.getImageUrl());
            }
            return;
        }

        response.setFreeVariantId(variant.getId());
        response.setFreeVariantName(variant.getName());
        response.setFreeVariantSku(variant.getSku());
        if (product != null) {
            response.setFreeProductId(product.getId());
            response.setFreeProductName(product.getName());
            response.setFreeProductImage(product.getImageUrl());
        }
    }

    public record AppliedFreeItem(FreeItemOffer offer, Variant variant, int quantity) {
    }
}
