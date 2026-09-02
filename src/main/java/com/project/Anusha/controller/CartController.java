package com.project.Anusha.controller;

import com.project.Anusha.dto.AddToCartRequest;
import com.project.Anusha.dto.CartItemDto;
import com.project.Anusha.dto.CartResponse;
import com.project.Anusha.model.Cart;
import com.project.Anusha.model.CartItem;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Product;
import com.project.Anusha.model.Variant;
import com.project.Anusha.service.CartService;
import com.project.Anusha.service.CheckoutSettingsService;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.FreeItemOfferService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;
    private final CustomerService customerService;
    private final CheckoutSettingsService checkoutSettingsService;
    private final FreeItemOfferService freeItemOfferService;

    public CartController(CartService cartService, CustomerService customerService, CheckoutSettingsService checkoutSettingsService,
                          FreeItemOfferService freeItemOfferService) {
        this.cartService = cartService;
        this.customerService = customerService;
        this.checkoutSettingsService = checkoutSettingsService;
        this.freeItemOfferService = freeItemOfferService;
    }

    private Customer getCustomer(UserDetails userDetails) {
        return customerService.getCustomerByPhone(userDetails.getUsername());
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal UserDetails userDetails) {
        Customer customer = getCustomer(userDetails);
        Cart cart = cartService.getCart(customer);
        return ResponseEntity.ok(buildCartResponse(cart));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@AuthenticationPrincipal UserDetails userDetails,
                                                 @RequestBody AddToCartRequest request) {
        Customer customer = getCustomer(userDetails);
        cartService.addItem(customer, request.getVariantId(), request.getQuantity());  // variantId
        return getCart(userDetails);
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> updateItem(@AuthenticationPrincipal UserDetails userDetails,
                                                    @PathVariable Long itemId,
                                                    @RequestParam Integer quantity) {
        Customer customer = getCustomer(userDetails);
        cartService.updateItemQuantity(customer, itemId, quantity);
        return getCart(userDetails);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(@AuthenticationPrincipal UserDetails userDetails,
                                                    @PathVariable Long itemId) {
        Customer customer = getCustomer(userDetails);
        cartService.removeItem(customer, itemId);
        return getCart(userDetails);
    }

    @DeleteMapping
    public ResponseEntity<?> clearCart(@AuthenticationPrincipal UserDetails userDetails) {
        Customer customer = getCustomer(userDetails);
        cartService.clearCart(customer);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/merge")
    public ResponseEntity<CartResponse> mergeCart(@AuthenticationPrincipal UserDetails userDetails,
                                                 @RequestBody java.util.List<com.project.Anusha.dto.GuestCartItemDto> guestItems) {
        Customer customer = getCustomer(userDetails);
        cartService.mergeCart(customer, guestItems);
        return getCart(userDetails);
    }


    private CartItemDto toDto(CartItem item) {
        CartItemDto dto = new CartItemDto();
        dto.setId(item.getId());
        Variant variant = item.getVariant();
        if (variant == null) {
            return null;
        }
        Product product = variant.getProduct();
        dto.setVariantId(variant.getId());
        dto.setVariantName(variant.getName());
        dto.setProductId(product != null ? product.getId() : null);
        dto.setProductName(product != null ? product.getName() : "");
        dto.setProductImage(product != null ? product.getImageUrl() : null);
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        dto.setFreeItem(false);
        return dto;
    }

    private CartItemDto toFreeDto(FreeItemOfferService.AppliedFreeItem item) {
        CartItemDto dto = new CartItemDto();
        dto.setVariantId(item.variant().getId());
        dto.setVariantName(item.variant().getName());
        dto.setProductId(item.variant().getProduct().getId());
        dto.setProductName(item.variant().getProduct().getName());
        dto.setProductImage(item.variant().getProduct().getImageUrl());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(BigDecimal.ZERO);
        dto.setTotalPrice(BigDecimal.ZERO);
        dto.setFreeItem(true);
        dto.setOfferName(item.offer().getName());
        return dto;
    }

    private CartResponse buildCartResponse(Cart cart) {
        CartResponse response = new CartResponse();
        response.setCartId(cart.getId());
        response.setItems(cart.getItems().stream().map(this::toDto).filter(java.util.Objects::nonNull).collect(Collectors.toList()));
        List<CartItemDto> freeItems = freeItemOfferService.getApplicableFreeItems(cart).stream()
                .map(this::toFreeDto)
                .collect(Collectors.toList());
        response.setFreeItems(freeItems);

        BigDecimal subtotal = cart.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveryCharge = cart.getItems().isEmpty()
                ? BigDecimal.ZERO
                : checkoutSettingsService.calculateDeliveryCharge(subtotal);
        BigDecimal platformFee = cart.getItems().isEmpty()
                ? BigDecimal.ZERO
                : checkoutSettingsService.getPlatformFee();
        BigDecimal smallCartFee = cart.getItems().isEmpty()
                ? BigDecimal.ZERO
                : checkoutSettingsService.calculateSmallCartFee(subtotal);

        response.setSubtotal(subtotal);
        response.setEstimatedDeliveryCharge(deliveryCharge);
        response.setDeliveryCharge(deliveryCharge);
        response.setPlatformFee(platformFee);
        response.setGrandTotal(subtotal.add(deliveryCharge).add(platformFee).add(smallCartFee));
        return response;
    }
}
