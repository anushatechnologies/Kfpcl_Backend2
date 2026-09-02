package com.project.Anusha.service;

import com.project.Anusha.exception.ResourceNotFoundException;
import com.project.Anusha.model.*;
import com.project.Anusha.repository.CouponRepository;
import com.project.Anusha.repository.DeliveryOrderRepository;
import com.project.Anusha.repository.DeliveryPersonRepository;
import com.project.Anusha.repository.OrderItemRepository;
import com.project.Anusha.repository.OrderRepository;
import com.project.Anusha.repository.StoreSubOrderRepository;
import com.project.Anusha.repository.VariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final AddressService addressService;
    private final ProductService productService;
    private final FirebaseService firebaseService;
    private final UserMainService userMainService;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final CouponRepository couponRepository;
    private final CouponService couponService;
    private final StoreOrderDispatchService storeOrderDispatchService;
    private final CheckoutSettingsService checkoutSettingsService;
    private final DeliveryPersonRepository deliveryPersonRepository;
    private final StoreSubOrderRepository storeSubOrderRepository;
    private final AppNotificationService appNotificationService;
    private final WalletService walletService;
    private final PaymentService paymentService;
    private final FreeItemOfferService freeItemOfferService;
    private final WhatsAppService whatsAppService;
    private final VariantRepository variantRepository;
    private final AdminPushTokenService adminPushTokenService;
    private final ExpoPushService expoPushService;

    // ---------- Customer methods ----------

    public List<com.project.Anusha.dto.ProductResponse> getRecentOrderProducts(Customer customer) {
        List<Product> products = orderItemRepository.findRecentProductsByCustomer(customer);
        return products.stream()
                .map(productService::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public Order placeOrder(Customer customer, Long addressId, String paymentMethod, String couponCode, String idempotencyKey) {
        return placeOrder(customer, addressId, paymentMethod, couponCode, idempotencyKey, null);
    }

    @Transactional
    public Order placeOrder(Customer customer, Long addressId, String paymentMethod, String couponCode, String idempotencyKey, BigDecimal walletAmount) {
        String normalizedIdempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
        if (normalizedIdempotencyKey != null && !normalizedIdempotencyKey.isBlank()) {
            Order existingOrder = orderRepository.findByCustomerAndIdempotencyKey(customer, normalizedIdempotencyKey)
                    .orElse(null);
            if (existingOrder != null) {
                return existingOrder;
            }
        }

        // 1. Get customer's cart with all items, variants, products and stores pre-loaded
        Cart cart = cartService.getCartForCheckout(customer);
        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        // Compute subtotal early so we can fetch all checkout settings in one DB hit below.
        BigDecimal subtotal = cart.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        CheckoutSettingsService.OrderChargesContext checkoutCtx = checkoutSettingsService.getOrderChargesContext(subtotal);

        String normalizedPaymentMethod = paymentMethod == null ? "COD" : paymentMethod.trim().toUpperCase();
        boolean isCodBased    = "COD".equals(normalizedPaymentMethod) || "COD_WALLET".equals(normalizedPaymentMethod);
        boolean isOnlineBased = "ONLINE".equals(normalizedPaymentMethod) || "ONLINE_WALLET".equals(normalizedPaymentMethod);
        boolean isWalletOnly  = "WALLET".equals(normalizedPaymentMethod);
        boolean isPartialWallet = "COD_WALLET".equals(normalizedPaymentMethod) || "ONLINE_WALLET".equals(normalizedPaymentMethod);

        if (!isCodBased && !isOnlineBased && !isWalletOnly) {
            throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
        }
        if (isOnlineBased && !checkoutCtx.onlineEnabled()) {
            throw new IllegalArgumentException("Online payment is currently disabled by admin");
        }
        if (isCodBased && !checkoutCtx.codEnabled()) {
            throw new IllegalArgumentException("Cash on delivery is currently disabled by admin");
        }

        // Validate wallet account exists for wallet-involved payments
        if ((isWalletOnly || isPartialWallet) && (customer.getUserMain() == null || customer.getUserMain().getId() == null)) {
            throw new IllegalArgumentException("Wallet account not found for customer");
        }

        // 2. Validate address
        Address address = addressService.getAddressByIdAndCustomer(addressId, customer);

        // 3. Create order
        Order order = new Order();
        order.setCustomer(customer);
        order.setAddress(address);
        // Clamp walletAmount to [0, grandTotal] — computed after totals, so store temporarily
        order.setPaymentMethod(normalizedPaymentMethod);
        order.setPaymentStatus(isWalletOnly ? "PAID" : "pending");
        order.setOrderStatus(isWalletOnly ? "confirmed" : "PLACED");
        order.setIdempotencyKey(normalizedIdempotencyKey != null && !normalizedIdempotencyKey.isBlank()
                ? normalizedIdempotencyKey : null);

        // 4. Calculate totals (subtotal already computed above for the settings context)
        BigDecimal discount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;
        if (couponCode != null && !couponCode.isBlank()) {
            String normalizedCouponCode = couponCode.trim().toUpperCase();
            discount = couponService.validateAndCalculateDiscount(normalizedCouponCode, customer, subtotal);
            appliedCoupon = couponRepository.findByCodeAndIsActiveTrue(normalizedCouponCode)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid or inactive coupon code"));
        }
        BigDecimal deliveryCharge = checkoutCtx.deliveryCharge();
        BigDecimal platformFee = checkoutCtx.platformFee();
        BigDecimal smallCartFee = checkoutCtx.smallCartFee();
        boolean intraStateSupply = GstInvoiceDetails.isIntraState(address.getState());

        order.setSubtotal(subtotal);
        order.setDeliveryCharge(deliveryCharge);
        order.setPlatformFee(platformFee);
        order.setHandlingCharge(BigDecimal.ZERO);
        order.setSmallCartFee(smallCartFee);
        order.setTax(BigDecimal.ZERO);
        order.setTaxableAmount(BigDecimal.ZERO);
        order.setCgstAmount(BigDecimal.ZERO);
        order.setSgstAmount(BigDecimal.ZERO);
        order.setIgstAmount(BigDecimal.ZERO);
        order.setDiscount(discount);
        order.setGrandTotal(
                subtotal
                        .add(order.getDeliveryCharge())
                        .add(order.getPlatformFee())
                        .add(order.getSmallCartFee())
                        .add(order.getTax())
                        .subtract(order.getDiscount())
        );

        // Clamp wallet amount to actual grand total
        BigDecimal cappedWallet = BigDecimal.ZERO;
        if (isWalletOnly) {
            cappedWallet = order.getGrandTotal();
        } else if (isPartialWallet && walletAmount != null && walletAmount.compareTo(BigDecimal.ZERO) > 0) {
            cappedWallet = walletAmount.min(order.getGrandTotal());
        }
        order.setWalletAmount(cappedWallet);

        // 5. Save order once. Order.orderNumber is generated before insert.
        Order savedOrder = orderRepository.save(order);

        if (appliedCoupon != null) {
            couponService.recordUsage(appliedCoupon, customer, savedOrder);
        }

        // Deduct wallet immediately for WALLET and COD_WALLET orders.
        // For ONLINE_WALLET the deduction happens at payment initiation.
        if (isWalletOnly) {
            walletService.deductMoney(
                    customer.getUserMain().getId(),
                    savedOrder.getGrandTotal(),
                    "Wallet payment for order " + savedOrder.getOrderNumber()
            );
        } else if ("COD_WALLET".equals(normalizedPaymentMethod) && cappedWallet.compareTo(BigDecimal.ZERO) > 0) {
            walletService.deductMoney(
                    customer.getUserMain().getId(),
                    cappedWallet,
                    "Partial wallet for order " + savedOrder.getOrderNumber()
            );
        }

        // 6. Convert cart items to order items
        List<FreeItemOfferService.AppliedFreeItem> applicableFreeItems = freeItemOfferService.getApplicableFreeItems(cart);

        // Batch-fetch all needed variants (cart + free items) with product and store in one query,
        // so the per-item decrementStockIfAvailable calls (clearAutomatically=true) don't cause
        // repeated DB round-trips on the success path.
        Set<Long> allVariantIds = new HashSet<>();
        cart.getItems().forEach(item -> { if (item.getVariant() != null) allVariantIds.add(item.getVariant().getId()); });
        applicableFreeItems.forEach(fi -> { if (fi.variant() != null) allVariantIds.add(fi.variant().getId()); });
        Map<Long, Variant> variantCache = allVariantIds.isEmpty() ? new HashMap<>() :
                variantRepository.findAllByIdsWithProduct(new ArrayList<>(allVariantIds)).stream()
                        .collect(Collectors.toMap(Variant::getId, v -> v));

        List<OrderItem> allOrderItems = new ArrayList<>();

        for (CartItem cartItem : cart.getItems()) {
            Long variantId = cartItem.getVariant() != null ? cartItem.getVariant().getId() : null;
            Variant variant = consumeVariantStock(variantCache.get(variantId), variantId, cartItem.getQuantity(), "cart item");

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(savedOrder);
            orderItem.setVariant(variant);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPrice(cartItem.getUnitPrice());
            orderItem.setProductName(variant.getProduct().getName());
            orderItem.setVariantName(variant.getName());
            orderItem.setProductSku(variant.getSku());
            orderItem.setImageUrl(variant.getProduct().getImageUrl());
            orderItem.setFreeItem(false);
            orderItem.setTotalPrice(orderItem.getUnitPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity())));
            applyGstSnapshot(orderItem, variant.getProduct(), intraStateSupply);
            allOrderItems.add(orderItem);
        }

        for (FreeItemOfferService.AppliedFreeItem freeItem : applicableFreeItems) {
            Long variantId = freeItem.variant() != null ? freeItem.variant().getId() : null;
            Variant variant = consumeVariantStock(variantCache.get(variantId), variantId, freeItem.quantity(), "free item");
            Product product = variant.getProduct();
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(savedOrder);
            orderItem.setVariant(variant);
            orderItem.setQuantity(freeItem.quantity());
            orderItem.setUnitPrice(BigDecimal.ZERO);
            orderItem.setProductName(product.getName());
            orderItem.setVariantName(variant.getName());
            orderItem.setProductSku(variant.getSku());
            orderItem.setImageUrl(product.getImageUrl());
            orderItem.setFreeItem(true);
            orderItem.setOfferName(freeItem.offer().getName());
            orderItem.setTotalPrice(BigDecimal.ZERO);
            applyGstSnapshot(orderItem, product, intraStateSupply);
            allOrderItems.add(orderItem);
        }

        List<OrderItem> savedItems = orderItemRepository.saveAll(allOrderItems);
        savedOrder.getItems().addAll(savedItems);
        applyOrderTaxTotals(savedOrder, savedItems);
        savedOrder = orderRepository.save(savedOrder);

        // 7. Create corresponding DeliveryOrder entry
        DeliveryOrder deliveryOrder = new DeliveryOrder();
        deliveryOrder.setOrderNumber(savedOrder.getOrderNumber());
        deliveryOrder.setCustomerName(customer.getFirstName() + " " + customer.getLastName());
        deliveryOrder.setCustomerPhone(customer.getPhone());
        deliveryOrder.setDeliveryAddress(String.join(", ",
                java.util.stream.Stream.of(address.getFlatNumber(), address.getAddressLine1(), address.getArea(), address.getCity())
                        .filter(part -> part != null && !part.isBlank())
                        .toArray(String[]::new)));
        
        // Keep the first store as the primary pickup anchor.
        // Multi-store pickup metadata is derived dynamically from the parent order items.
        if (!cart.getItems().isEmpty()) {
            Store store = cart.getItems().get(0).getVariant().getProduct().getStore();
            if (store == null) {
                throw new IllegalArgumentException("Product has no store assigned. Cannot place order.");
            }
            deliveryOrder.setStore(store);
            deliveryOrder.setPickupAddress(buildPickupAddress(store));
            // Store GPS coords so distance can be calculated at pickup confirmation
            deliveryOrder.setPickupLatitude(store.getLatitude());
            deliveryOrder.setPickupLongitude(store.getLongitude());
        } else {
            throw new IllegalArgumentException("Cart is empty — cannot create delivery order.");
        }

        // Save customer delivery GPS coords for Haversine calculation at pickup
        if (address.getLatitude() != null && address.getLongitude() != null) {
            deliveryOrder.setDeliveryLatitude(address.getLatitude());
            deliveryOrder.setDeliveryLongitude(address.getLongitude());
        }

        deliveryOrder.setOrderAmount(resolveDeliveryOrderAmount(savedOrder));
        deliveryOrder.setDeliveryFee(savedOrder.getDeliveryCharge());
        deliveryOrder.setPaymentType(resolveDeliveryPaymentType(normalizedPaymentMethod));
        deliveryOrder.setPaidOnline(isPaidOnlineForDelivery(normalizedPaymentMethod));
        deliveryOrder.setStatus(DeliveryOrder.OrderStatus.PLACED);
        deliveryOrderRepository.save(deliveryOrder);

        // Full wallet payments are already settled at checkout.
        if ("PAID".equalsIgnoreCase(savedOrder.getPaymentStatus())) {
            storeOrderDispatchService.notifyOperatorOfPaidOrder(savedOrder);
        } else if (isCodBased) {
            // COD is unpaid, so it remains manually Accept/Reject-able, but the
            // operator still receives the order details immediately.
            storeOrderDispatchService.notifyOperatorOfOrder(savedOrder);
        }

        // 8. Clear cart
        cartService.clearCart(customer);

        // 9. Realtime Dashboard Update
        firebaseService.pushDashboardEvent("NEW_ORDER", savedOrder.getOrderNumber());

        // 9b. Mobile push to every active AdminApp device.
        // @Async on ExpoPushService — won't block the customer's checkout response.
        List<String> adminTokens = adminPushTokenService.findAllActive().stream()
                .map(AdminPushToken::getExpoPushToken)
                .filter(t -> t != null && !t.isBlank())
                .toList();
        if (!adminTokens.isEmpty()) {
            BigDecimal grandTotal = savedOrder.getGrandTotal();
            String customerLabel = (customer.getFirstName() == null ? "Customer" : customer.getFirstName())
                    + (grandTotal != null ? " · ₹" + grandTotal : "");
            expoPushService.sendNewOrderBroadcast(
                    adminTokens,
                    savedOrder.getId(),
                    "New order #" + savedOrder.getOrderNumber(),
                    customerLabel);
        }

        // Customer receives confirmation immediately after checkout. Store notification still happens after admin acceptance.
        whatsAppService.notifyCustomerOrderConfirmed(savedOrder);

        return orderRepository.findCustomerOrderDetailsById(savedOrder.getId()).orElse(savedOrder);
    }

    public List<Order> getCustomerOrders(Customer customer) {
        return orderRepository.findByCustomerOrderByPlacedAtDesc(customer);
    }

    private BigDecimal resolveDeliveryOrderAmount(Order order) {
        BigDecimal grandTotal = order.getGrandTotal() != null ? order.getGrandTotal() : BigDecimal.ZERO;
        String method = order.getPaymentMethod() == null ? "" : order.getPaymentMethod().trim().toUpperCase();
        if (!"COD_WALLET".equals(method) && !"ONLINE_WALLET".equals(method)) {
            return grandTotal;
        }

        BigDecimal walletApplied = order.getWalletAmount() != null ? order.getWalletAmount() : BigDecimal.ZERO;
        BigDecimal remainingAmount = grandTotal.subtract(walletApplied);
        return remainingAmount.compareTo(BigDecimal.ZERO) > 0 ? remainingAmount : BigDecimal.ZERO;
    }

    private void applyGstSnapshot(OrderItem orderItem, Product product, boolean intraStateSupply) {
        BigDecimal gstRate = product != null && product.getGstRate() != null ? product.getGstRate() : BigDecimal.ZERO;
        BigDecimal grossAmount = orderItem.getTotalPrice() != null ? orderItem.getTotalPrice() : BigDecimal.ZERO;
        BigDecimal taxableAmount = taxableFromInclusive(grossAmount, gstRate);
        BigDecimal totalTax = grossAmount.subtract(taxableAmount).setScale(2, RoundingMode.HALF_UP);

        orderItem.setHsnCode(product != null ? product.getHsnCode() : null);
        orderItem.setGstRate(gstRate.setScale(2, RoundingMode.HALF_UP));
        orderItem.setTaxableAmount(taxableAmount);
        orderItem.setTotalTaxAmount(totalTax);

        if (intraStateSupply) {
            BigDecimal halfRate = gstRate.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            BigDecimal cgst = totalTax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            BigDecimal sgst = totalTax.subtract(cgst).setScale(2, RoundingMode.HALF_UP);
            orderItem.setCgstRate(halfRate);
            orderItem.setSgstRate(halfRate);
            orderItem.setIgstRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            orderItem.setCgstAmount(cgst);
            orderItem.setSgstAmount(sgst);
            orderItem.setIgstAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        } else {
            orderItem.setCgstRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            orderItem.setSgstRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            orderItem.setIgstRate(gstRate.setScale(2, RoundingMode.HALF_UP));
            orderItem.setCgstAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            orderItem.setSgstAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            orderItem.setIgstAmount(totalTax);
        }
    }

    private BigDecimal taxableFromInclusive(BigDecimal grossAmount, BigDecimal gstRate) {
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (gstRate == null || gstRate.compareTo(BigDecimal.ZERO) <= 0) {
            return grossAmount.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal divisor = BigDecimal.valueOf(100).add(gstRate);
        return grossAmount.multiply(BigDecimal.valueOf(100)).divide(divisor, 2, RoundingMode.HALF_UP);
    }

    private void applyOrderTaxTotals(Order order, List<OrderItem> items) {
        BigDecimal taxable = sum(items, OrderItem::getTaxableAmount);
        BigDecimal cgst = sum(items, OrderItem::getCgstAmount);
        BigDecimal sgst = sum(items, OrderItem::getSgstAmount);
        BigDecimal igst = sum(items, OrderItem::getIgstAmount);
        order.setTaxableAmount(taxable);
        order.setCgstAmount(cgst);
        order.setSgstAmount(sgst);
        order.setIgstAmount(igst);
        order.setTax(cgst.add(sgst).add(igst).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal sum(List<OrderItem> items, java.util.function.Function<OrderItem, BigDecimal> mapper) {
        return items.stream()
                .map(mapper)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private DeliveryOrder.PaymentType resolveDeliveryPaymentType(String paymentMethod) {
        String method = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase();
        if ("COD".equals(method) || "COD_WALLET".equals(method)) {
            return DeliveryOrder.PaymentType.COD;
        }
        if ("UPI".equals(method)) {
            return DeliveryOrder.PaymentType.UPI;
        }
        return DeliveryOrder.PaymentType.ONLINE_PAID;
    }

    private boolean isPaidOnlineForDelivery(String paymentMethod) {
        String method = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase();
        return !("COD".equals(method) || "COD_WALLET".equals(method));
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findCustomerOrderDetailsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }

    @Transactional
    public Order cancelOrderByCustomer(Customer customer, Long orderId, String reason) {
        Order order = getOrderById(orderId);

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("Order does not belong to the authenticated customer");
        }

        String normalizedStatus = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim().toLowerCase();
        if ("delivered".equals(normalizedStatus)) {
            throw new IllegalArgumentException("Delivered orders cannot be cancelled");
        }
        if ("cancelled".equals(normalizedStatus) || "payment_failed".equals(normalizedStatus)) {
            return order;
        }

        order.setOrderStatus("cancelled");
        order.setCancellationReason(
                reason != null && !reason.isBlank() ? reason.trim() : "Cancelled by customer");
        order.setCancelledAt(LocalDateTime.now());
        restoreStockForOrder(order);

        refundWalletIfApplicable(order, "Refund for cancelled order " + order.getOrderNumber());
        refundOnlineIfApplicable(order, "Refund for cancelled order " + order.getOrderNumber());

        Order savedOrder = orderRepository.save(order);

        deliveryOrderRepository.findByOrderNumber(savedOrder.getOrderNumber()).ifPresent(deliveryOrder -> {
            deliveryOrder.setStatus(DeliveryOrder.OrderStatus.CANCELLED);
            deliveryOrderRepository.save(deliveryOrder);
        });

        notifyCustomer(savedOrder, "ORDER_CANCELLED", "Order Cancelled",
                "Your order #" + savedOrder.getOrderNumber() + " has been cancelled. Your wallet has been refunded.");

        return savedOrder;
    }

    // ---------- Admin methods ----------

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAllOrderByPlacedAtDesc();
    }

    @Transactional(readOnly = true)
    public Order getOrderByIdForAdmin(Long orderId) {
        return orderRepository.findByIdWithAddressAndCustomer(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }

    public Order getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));
    }

    @Transactional
    public Order acceptOrder(Long orderId) {
        Order order = getOrderByIdForAdmin(orderId);
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())
                && "confirmed".equalsIgnoreCase(order.getOrderStatus())) {
            storeOrderDispatchService.notifyOperatorOfPaidOrder(order);
            return order;
        }
        if (!"PLACED".equalsIgnoreCase(order.getOrderStatus())) {
             // Fallback for older orders if needed
             if (!"pending".equalsIgnoreCase(order.getOrderStatus())) {
                 throw new IllegalArgumentException("Order cannot be accepted because it is not in PLACED/pending state.");
             }
        }
        order.setOrderStatus("confirmed");
        Order saved = orderRepository.save(order);

        // Notify customer
        notifyCustomer(saved, "ORDER_CONFIRMED", "Order Confirmed",
                "Your order #" + saved.getOrderNumber() + " has been confirmed and is being processed.");

        dispatchOrderToStores(saved);
        return saved;
    }

    @Transactional
    public Order rejectOrder(Long orderId, String reason) {
        Order order = getOrderByIdForAdmin(orderId);
        String normalizedStatus = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim().toLowerCase();
        if ("delivered".equals(normalizedStatus)) {
            throw new IllegalArgumentException("Delivered orders cannot be rejected");
        }
        if ("cancelled".equals(normalizedStatus) || "payment_failed".equals(normalizedStatus)) {
            return order;
        }
        order.setOrderStatus("cancelled");
        order.setCancellationReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        restoreStockForOrder(order);

        refundWalletIfApplicable(order, "Refund for rejected order " + order.getOrderNumber());
        refundOnlineIfApplicable(order, "Refund for rejected order " + order.getOrderNumber());

        Order saved = orderRepository.save(order);

        // Notify customer
        notifyCustomer(saved, "ORDER_CANCELLED", "Order Cancelled",
                "Your order #" + saved.getOrderNumber() + " has been cancelled. Reason: " + reason
                + ("WALLET".equalsIgnoreCase(saved.getPaymentMethod()) ? " Your wallet has been refunded." : ""));

        return saved;
    }

    @Transactional
    public Map<String, Object> assignDeliveryPerson(Long orderId, Long deliveryPersonId) {
        Order order = getOrderByIdForAdmin(orderId);
        if (!"confirmed".equalsIgnoreCase(order.getOrderStatus())) {
            throw new IllegalArgumentException("Order must be in 'confirmed' state before assigning a delivery person");
        }

        DeliveryPerson deliveryPerson = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery person not found with id: " + deliveryPersonId));

        order.setOrderStatus("assigned");
        orderRepository.save(order);

        deliveryOrderRepository.findByOrderNumber(order.getOrderNumber()).ifPresent(deliveryOrder -> {
            deliveryOrder.setDeliveryPerson(deliveryPerson);
            deliveryOrder.setAssignedAt(LocalDateTime.now());
            deliveryOrder.setStatus(DeliveryOrder.OrderStatus.RIDER_ASSIGNED);
            deliveryOrderRepository.save(deliveryOrder);

            if (deliveryPerson.getUserMain() != null) {
                appNotificationService.notifyUser(
                        deliveryPerson.getUserMain(),
                        "ORDER_ASSIGNED",
                        "OrderDetail",
                        "New Delivery Job",
                        "You have been assigned order #" + order.getOrderNumber() + ". Head to the store!",
                        deliveryOrder.getId(),
                        deliveryOrder.getOrderNumber(),
                        null
                );
            }
        });

        notifyCustomer(order, "ORDER_ASSIGNED", "Rider Assigned",
                "A delivery rider has been assigned to your order #" + order.getOrderNumber() + ".");

        firebaseService.pushDashboardEvent("RIDER_ASSIGNED", order.getOrderNumber());

        return Map.of(
                "success", true,
                "message", "Delivery person assigned successfully",
                "orderId", orderId,
                "orderNumber", order.getOrderNumber()
        );
    }

    @Transactional
    public Map<String, Object> generateDeliveryOtp(Long orderId) {
        Order order = getOrderByIdForAdmin(orderId);
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findByOrderNumber(order.getOrderNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery order not found for order: " + order.getOrderNumber()));

        String otp = String.format("%06d", (int) (Math.random() * 1_000_000));
        deliveryOrder.setDeliveryOtp(otp);
        deliveryOrder.setDeliveryOtpExpiry(LocalDateTime.now().plusHours(2));
        deliveryOrderRepository.save(deliveryOrder);

        return Map.of(
                "success", true,
                "message", "Delivery OTP generated",
                "deliveryOtp", otp
        );
    }

    /** Public entrypoint so admin can re-send store notifications without accepting the order. */
    public void notifyStoreOfOrder(Long orderId) {
        Order order = getOrderByIdForAdmin(orderId);
        dispatchOrderToStores(order);
    }

    private void dispatchOrderToStores(Order order) {
        Map<Long, StoreDispatchPayload> storeDispatches = buildStoreDispatches(order);
        if (storeDispatches.isEmpty()) {
            return;
        }

        DeliveryOrder deliveryOrder = deliveryOrderRepository.findByOrderNumber(order.getOrderNumber()).orElse(null);
        if (deliveryOrder == null) {
            System.err.println("[ORDER] No DeliveryOrder found for " + order.getOrderNumber() + " — skipping store notifications");
            return;
        }

        String customerName = order.getCustomer() != null ? order.getCustomer().getName() : "Customer";
        storeDispatches.values().forEach(payload -> {
            try {
                StoreSubOrder subOrder = storeSubOrderRepository
                        .findByOrderIdAndStoreId(order.getId(), payload.store().getId())
                        .orElseGet(StoreSubOrder::new);
                subOrder.setOrder(order);
                subOrder.setStore(payload.store());
                subOrder.setItemSummary(payload.itemSummary().toString());
                subOrder.setSubtotal(payload.subtotal());
                subOrder.setStatus(StoreSubOrder.SubOrderStatus.STORE_NOTIFIED);
                subOrder.setStoreNotifiedAt(LocalDateTime.now());
                storeSubOrderRepository.save(subOrder);

                storeOrderDispatchService.notifyStoreOfSubOrder(subOrder, customerName);
            } catch (Exception e) {
                System.err.println("[ORDER] Store notification failed for " + payload.store().getName()
                        + " on order " + order.getOrderNumber() + ": " + e.getMessage());
            }
        });

        deliveryOrder.setStatus(DeliveryOrder.OrderStatus.STORE_NOTIFIED);
        deliveryOrder.setNotificationSentAt(LocalDateTime.now());
        deliveryOrderRepository.save(deliveryOrder);

        firebaseService.pushDashboardEvent("STORE_NOTIFIED", order.getOrderNumber());
    }

    private Map<Long, StoreDispatchPayload> buildStoreDispatches(Order order) {
        Map<Long, StoreDispatchPayload> groupedStores = new LinkedHashMap<>();

        for (OrderItem item : order.getItems()) {
            Product product = item.getVariant() != null ? item.getVariant().getProduct() : null;
            Store store = product != null ? product.getStore() : null;
            if (store == null || store.getId() == null) {
                continue;
            }

            StoreDispatchPayload current = groupedStores.get(store.getId());
            String itemLabel = item.getProductName() + " (" + item.getQuantity() + ")";

            if (current == null) {
                groupedStores.put(
                        store.getId(),
                        new StoreDispatchPayload(
                                store,
                                new StringBuilder(itemLabel),
                                item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO));
                continue;
            }

            if (!current.itemSummary().isEmpty()) {
                current.itemSummary().append(", ");
            }
            current.itemSummary().append(itemLabel);
            current.subtotal = current.subtotal().add(
                    item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO);
        }

        return groupedStores;
    }

    private void refundWalletIfApplicable(Order order, String description) {
        if (order.getCustomer() == null || order.getCustomer().getUserMain() == null
                || order.getCustomer().getUserMain().getId() == null) return;

        String method = order.getPaymentMethod() == null ? "" : order.getPaymentMethod().toUpperCase();
        BigDecimal refundAmt = BigDecimal.ZERO;

        if ("WALLET".equals(method)) {
            refundAmt = order.getGrandTotal();
        } else if ("COD_WALLET".equals(method) || "ONLINE_WALLET".equals(method)) {
            // For ONLINE_WALLET only refund if wallet was actually deducted (order was PAID)
            boolean walletWasDeducted = "COD_WALLET".equals(method)
                    || "PAID".equalsIgnoreCase(order.getPaymentStatus());
            if (walletWasDeducted && order.getWalletAmount() != null
                    && order.getWalletAmount().compareTo(BigDecimal.ZERO) > 0) {
                refundAmt = order.getWalletAmount();
            }
        }

        if (refundAmt.compareTo(BigDecimal.ZERO) > 0) {
            walletService.addMoney(order.getCustomer().getUserMain().getId(), refundAmt, description);
        }
    }

    private void refundOnlineIfApplicable(Order order, String description) {
        String method = order.getPaymentMethod() == null ? "" : order.getPaymentMethod().toUpperCase();
        if (!("ONLINE".equals(method) || "ONLINE_WALLET".equals(method))
                || !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return;
        }
        try {
            paymentService.refundForOrderIfEligible(order, description);
        } catch (Exception e) {
            notifyCustomer(order, "REFUND_PENDING", "Refund Pending",
                    "Your refund for order #" + order.getOrderNumber()
                            + " could not be processed automatically. Our team will review it.");
        }
    }

    private String buildPickupAddress(Store store) {
        String pickupAddr = store.getAddress();
        if (pickupAddr == null || pickupAddr.isBlank()) {
            pickupAddr = store.getName()
                    + (store.getCity() != null ? ", " + store.getCity() : "")
                    + (store.getPincode() != null ? " - " + store.getPincode() : "");
        }
        return pickupAddr;
    }

    private Variant consumeVariantStock(Variant preloaded, Long variantId, Integer quantity, String context) {
        if (variantId == null) {
            throw new IllegalArgumentException("Variant is missing for " + context);
        }

        int requestedQuantity = quantity == null ? 0 : quantity;
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("Invalid quantity for " + context);
        }
        if (preloaded == null) {
            throw new ResourceNotFoundException("Variant not found with id: " + variantId);
        }
        if (!Boolean.TRUE.equals(preloaded.getIsActive())) {
            throw new IllegalArgumentException("Out of stock");
        }

        int updatedRows = variantRepository.decrementStockIfAvailable(variantId, requestedQuantity);
        if (updatedRows == 0) {
            // Re-fetch only on failure to get actual stock count (context was cleared by clearAutomatically=true)
            Variant latest = variantRepository.findById(variantId).orElse(preloaded);
            int latestStock = latest.getStock() == null ? 0 : latest.getStock();
            if (latestStock <= 0) {
                throw new IllegalArgumentException("Out of stock");
            }
            throw new IllegalArgumentException("Only " + latestStock + " items available");
        }

        // preloaded entity is detached after clearAutomatically=true, but all EAGER fields
        // (product name, imageUrl, store, etc.) are still accessible in memory — no re-fetch needed.
        return preloaded;
    }

    private void restoreStockForOrder(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }

        for (OrderItem item : order.getItems()) {
            if (item.getVariant() == null || item.getVariant().getId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }

            variantRepository.restoreStock(item.getVariant().getId(), item.getQuantity());
        }
    }

    private static final class StoreDispatchPayload {
        private final Store store;
        private final StringBuilder itemSummary;
        private BigDecimal subtotal;

        private StoreDispatchPayload(Store store, StringBuilder itemSummary, BigDecimal subtotal) {
            this.store = store;
            this.itemSummary = itemSummary;
            this.subtotal = subtotal;
        }

        private Store store() {
            return store;
        }

        private StringBuilder itemSummary() {
            return itemSummary;
        }

        private BigDecimal subtotal() {
            return subtotal;
        }
    }

    private void notifyCustomer(Order order, String type, String title, String body) {
        if (order.getCustomer() == null || order.getCustomer().getUserMain() == null) {
            return;
        }

        appNotificationService.notifyUser(
                order.getCustomer().getUserMain(),
                type,
                "OrderTracking",
                title,
                body,
                order.getId(),
                order.getOrderNumber(),
                null
        );
    }
}
