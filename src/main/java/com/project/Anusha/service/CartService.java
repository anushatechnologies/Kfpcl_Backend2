package com.project.Anusha.service;

import com.project.Anusha.dto.GuestCartItemDto;
import com.project.Anusha.model.*;
import com.project.Anusha.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class CartService {

    private static final Logger logger = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final VariantRepository variantRepository;   // new dependency

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       VariantRepository variantRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.variantRepository = variantRepository;
    }

    public Cart getOrCreateCart(Customer customer) {
        return cartRepository.findByCustomer(customer)
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setCustomer(customer);
                    return cartRepository.save(cart);
                });
    }

    @Transactional
    public Cart addItem(Customer customer, Long variantId, Integer quantity) {
        if (variantId == null) {
            logger.error("Attempted to add item to cart with null variantId for customer: {}", customer.getId());
            throw new IllegalArgumentException("Variant ID is required");
        }
        if (quantity == null || quantity <= 0) {
            quantity = 1;
        }

        Cart cart = getOrCreateCart(customer);

        Variant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> {
                    logger.error("Variant not found: {} while adding to cart for customer: {}", variantId, customer.getId());
                    return new RuntimeException("Variant not found");
                });

        if (!variant.getIsActive()) {
            logger.warn("Attempted to add inactive variant: {} to cart for customer: {}", variantId, customer.getId());
            throw new RuntimeException("Variant is not available");
        }
        BigDecimal effectivePrice = variant.getDiscountPrice() != null
                ? BigDecimal.valueOf(variant.getDiscountPrice())
                : BigDecimal.valueOf(variant.getPrice());

        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndVariantIdForUpdate(cart.getId(), variantId);
        int existingQuantity = existingItem.map(CartItem::getQuantity).orElse(0);
        int requestedQuantity = existingQuantity + quantity;
        int availableStock = variant.getStock() == null ? 0 : variant.getStock();
        if (availableStock < requestedQuantity) {
            logger.warn("Insufficient stock for variant: {} (Required: {}, Available: {})", variantId, requestedQuantity, availableStock);
            throw new RuntimeException("Only " + availableStock + " items available");
        }

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(requestedQuantity);
            item.setUnitPrice(effectivePrice);
            cartItemRepository.save(item);
            logger.info("Updated existing cart item: {} (New Quantity: {})", item.getId(), item.getQuantity());
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setVariant(variant);
            newItem.setQuantity(quantity);
            newItem.setUnitPrice(effectivePrice);
            cartItemRepository.save(newItem);
            logger.info("Added new cart item to cart: {} for variant: {}", cart.getId(), variantId);
        }

        return cart;
    }

    @Transactional
    public void mergeCart(Customer customer, List<GuestCartItemDto> guestItems) {
        if (guestItems == null || guestItems.isEmpty()) {
            logger.info("No guest items to merge for customer: {}", customer.getId());
            return;
        }
        Map<Long, Integer> quantitiesByVariant = guestItems.stream()
                .filter(item -> item.getVariantId() != null)
                .collect(Collectors.toMap(
                        GuestCartItemDto::getVariantId,
                        item -> item.getQuantity() == null || item.getQuantity() <= 0 ? 1 : item.getQuantity(),
                        Integer::sum
                ));

        logger.info("Merging {} guest variants for customer: {}", quantitiesByVariant.size(), customer.getId());
        for (Map.Entry<Long, Integer> item : quantitiesByVariant.entrySet()) {
            try {
                addItem(customer, item.getKey(), item.getValue());
            } catch (Exception e) {
                logger.error("Failed to merge guest item: variantId={}, error={}", item.getKey(), e.getMessage());
                // Skip items that fail (e.g. out of reached stock or invalid variant)
            }
        }
    }


    @Transactional
    public Cart updateItemQuantity(Customer customer, Long itemId, Integer quantity) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        if (!Objects.equals(item.getCart().getCustomer().getId(), customer.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            Variant variant = item.getVariant();
            int availableStock = variant.getStock() == null ? 0 : variant.getStock();
            if (availableStock < quantity) {
                throw new RuntimeException("Only " + availableStock + " items available");
            }
            // Refresh unit price from current variant (optional but recommended)
            BigDecimal effectivePrice = variant.getDiscountPrice() != null
                    ? BigDecimal.valueOf(variant.getDiscountPrice())
                    : BigDecimal.valueOf(variant.getPrice());
            item.setUnitPrice(effectivePrice);

            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return item.getCart();
    }

    @Transactional
    public void removeItem(Customer customer, Long itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        if (!Objects.equals(item.getCart().getCustomer().getId(), customer.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        cartItemRepository.delete(item);
    }

    public Cart getCart(Customer customer) {
        return cartRepository.findByCustomerWithItems(customer)
                .orElseGet(() -> getOrCreateCart(customer));
    }

    public Cart getCartForCheckout(Customer customer) {
        return cartRepository.findByCustomerWithItemsForCheckout(customer)
                .orElseGet(() -> getOrCreateCart(customer));
    }

    @Transactional
    public void clearCart(Customer customer) {
        Cart cart = getCart(customer);
        cartItemRepository.deleteByCartId(cart.getId());
    }
}
