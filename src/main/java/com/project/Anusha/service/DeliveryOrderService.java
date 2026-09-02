package com.project.Anusha.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.Order;
import com.project.Anusha.model.OrderItem;
import com.project.Anusha.model.StoreSubOrder;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.repository.DeliveryOrderRepository;
import com.project.Anusha.repository.DeliveryPersonRepository;
import com.project.Anusha.repository.OrderRepository;
import com.project.Anusha.repository.StoreSubOrderRepository;
import com.project.Anusha.repository.VariantRepository;

@Service
@Transactional
public class DeliveryOrderService {

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private DeliveryPersonRepository deliveryPersonRepository;

    @Autowired
    private DeliveryPersonService deliveryPersonService;

    @Autowired
    private UserMainService userMainService;

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private WhatsAppService whatsAppService;

    @Autowired
    private FareRuleService fareRuleService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StoreSubOrderRepository storeSubOrderRepository;

    @Autowired
    private AppNotificationService appNotificationService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private VariantRepository variantRepository;

    /**
     * Get order by ID
     */
    public DeliveryOrder getOrderById(Long id) {
        return deliveryOrderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + id));
    }

    /**
     * Get order by order number
     */
    public Optional<DeliveryOrder> getOrderByOrderNumber(String orderNumber) {
        return deliveryOrderRepository.findByOrderNumber(orderNumber);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STORE WORKFLOW (WhatsApp)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Processes store response from WhatsApp webhook.
     */
    public void handleStoreResponse(String orderNumber, String response, String remarks) {
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));

        if ("ACCEPT".equalsIgnoreCase(response)) {
            order.setStatus(DeliveryOrder.OrderStatus.STORE_ACCEPTED);
            order.setStoreRemarks(remarks);
            
            // Advance to broadcast
            generateAndSendPickupOtp(order);
            broadcastToRiders(order);
            
            firebaseService.pushDashboardEvent("STORE_ACCEPTED", orderNumber);
        } else if ("REJECT".equalsIgnoreCase(response)) {
            order.setStatus(DeliveryOrder.OrderStatus.STORE_REJECTED);
            order.setStoreRemarks(remarks);
            firebaseService.pushDashboardEvent("STORE_REJECTED", orderNumber);

            orderRepository.findByOrderNumber(orderNumber).ifPresent(parentOrder -> {
                restoreStockIfOrderStillActive(parentOrder);
                parentOrder.setOrderStatus("cancelled");
                parentOrder.setCancellationReason("Store rejected: " + (remarks == null ? "" : remarks.trim()));
                parentOrder.setCancelledAt(LocalDateTime.now());
                orderRepository.save(parentOrder);

                refundWalletIfApplicable(parentOrder, "Refund for store-rejected order " + orderNumber);
                refundOnlineIfApplicable(parentOrder, "Refund for store-rejected order " + orderNumber);
            });

            // Notify Customer: Store busy/rejected
            notifyCustomer(order, "ORDER_CANCELLED", "Order Update",
                "The store is unable to process your order at this time. Refund has been credited to your wallet.");
        }

        deliveryOrderRepository.save(order);
    }

    public void handleStoreSubOrderResponse(Long subOrderId, String response, String remarks) {
        StoreSubOrder subOrder = storeSubOrderRepository.findById(subOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-order not found: " + subOrderId));

        Order parentOrder = subOrder.getOrder();
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findByOrderNumber(parentOrder.getOrderNumber())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Delivery order not found for order: " + parentOrder.getOrderNumber()));

        if ("ACCEPT".equalsIgnoreCase(response)) {
            subOrder.setStoreAcceptedAt(LocalDateTime.now());
            subOrder.setStatus(StoreSubOrder.SubOrderStatus.STORE_ACCEPTED);

            ensurePickupOtp(deliveryOrder);
            whatsAppService.sendPickupOtpToStore(deliveryOrder, subOrder.getStore());
            subOrder.setStatus(StoreSubOrder.SubOrderStatus.OTP_SENT);
            storeSubOrderRepository.save(subOrder);

            List<StoreSubOrder> allSubOrders = storeSubOrderRepository.findByOrderId(parentOrder.getId());
            boolean allStoresAccepted = !allSubOrders.isEmpty() && allSubOrders.stream().allMatch(existing ->
                    existing.getStatus() == StoreSubOrder.SubOrderStatus.STORE_ACCEPTED
                            || existing.getStatus() == StoreSubOrder.SubOrderStatus.OTP_SENT
                            || existing.getStatus() == StoreSubOrder.SubOrderStatus.PICKED_UP);

            if (allStoresAccepted) {
                DeliveryOrder.OrderStatus previousStatus = deliveryOrder.getStatus();
                deliveryOrder.setStatus(DeliveryOrder.OrderStatus.PICKUP_OTP_GENERATED);
                deliveryOrderRepository.save(deliveryOrder);
                if (previousStatus != DeliveryOrder.OrderStatus.BROADCASTED_TO_RIDERS
                        && previousStatus != DeliveryOrder.OrderStatus.RIDER_ASSIGNED
                        && previousStatus != DeliveryOrder.OrderStatus.REACHED_STORE
                        && previousStatus != DeliveryOrder.OrderStatus.PICKED_UP
                        && previousStatus != DeliveryOrder.OrderStatus.OUT_FOR_DELIVERY
                        && previousStatus != DeliveryOrder.OrderStatus.DELIVERED) {
                    broadcastToRiders(deliveryOrder);
                }
            } else {
                deliveryOrderRepository.save(deliveryOrder);
            }

            firebaseService.pushDashboardEvent("STORE_ACCEPTED", Map.of(
                    "orderNumber", deliveryOrder.getOrderNumber(),
                    "storeId", subOrder.getStore().getId(),
                    "storeName", subOrder.getStore().getName(),
                    "subOrderId", subOrderId
            ));
            return;
        }

        if ("REJECT".equalsIgnoreCase(response)) {
            subOrder.setStatus(StoreSubOrder.SubOrderStatus.STORE_REJECTED);
            storeSubOrderRepository.save(subOrder);

            deliveryOrder.setStatus(DeliveryOrder.OrderStatus.STORE_REJECTED);
            deliveryOrder.setStoreRemarks(remarks);
            deliveryOrderRepository.save(deliveryOrder);

            firebaseService.pushDashboardEvent("STORE_REJECTED", Map.of(
                    "orderNumber", deliveryOrder.getOrderNumber(),
                    "storeId", subOrder.getStore().getId(),
                    "storeName", subOrder.getStore().getName(),
                    "subOrderId", subOrderId
            ));

            // Re-fetch all sub-orders to check if every store has rejected.
            // Only cancel the parent order + refund wallet when ALL stores rejected.
            // Partial rejections are left to admin — a pro-rata refund is a future enhancement.
            List<StoreSubOrder> allSubOrders = storeSubOrderRepository.findByOrderId(parentOrder.getId());
            boolean allRejected = !allSubOrders.isEmpty() && allSubOrders.stream().allMatch(so ->
                    so.getStatus() == StoreSubOrder.SubOrderStatus.STORE_REJECTED
                    || so.getStatus() == StoreSubOrder.SubOrderStatus.CANCELLED);

            if (allRejected) {
                restoreStockIfOrderStillActive(parentOrder);
                parentOrder.setOrderStatus("cancelled");
                parentOrder.setCancellationReason("All stores rejected the order");
                parentOrder.setCancelledAt(LocalDateTime.now());
                orderRepository.save(parentOrder);

                refundWalletIfApplicable(parentOrder, "Refund for store-rejected order " + parentOrder.getOrderNumber());
                refundOnlineIfApplicable(parentOrder, "Refund for store-rejected order " + parentOrder.getOrderNumber());

                notifyCustomer(deliveryOrder, "ORDER_CANCELLED", "Order Cancelled",
                        "All stores were unable to process your order."
                        + (usesWalletPayment(parentOrder)
                           ? " Refund has been credited to your wallet." : " Our team will contact you."));
            } else {
                notifyCustomer(deliveryOrder, "ORDER_UPDATE", "Order Update",
                        "One of the stores is unable to process part of your order. Our team will contact you.");
            }
        }
    }

    private void refundOnlineIfApplicable(Order order, String reason) {
        String method = order.getPaymentMethod() == null ? "" : order.getPaymentMethod().toUpperCase();
        if (!("ONLINE".equals(method) || "ONLINE_WALLET".equals(method))
                || !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return;
        }
        try {
            paymentService.refundForOrderIfEligible(order, reason);
        } catch (Exception e) {
            notifyCustomerOrder(order, "REFUND_PENDING", "Refund Pending",
                    "Your refund for order #" + order.getOrderNumber()
                            + " could not be processed automatically. Our team will review it.");
        }
    }

    private void restoreStockIfOrderStillActive(Order order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }

        String status = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim();
        if ("cancelled".equalsIgnoreCase(status)
                || "PAYMENT_FAILED".equalsIgnoreCase(status)
                || "delivered".equalsIgnoreCase(status)) {
            return;
        }

        for (OrderItem item : order.getItems()) {
            if (item.getVariant() == null || item.getVariant().getId() == null
                    || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            variantRepository.restoreStock(item.getVariant().getId(), item.getQuantity());
        }
    }

    /**
     * Admin-triggered: regenerate and send pickup OTP to the store via WhatsApp.
     * @param orderNumber the order number from the Order entity (used to look up DeliveryOrder)
     */
    public void adminSendStorePickupOtp(String orderNumber) {
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Delivery order not found for: " + orderNumber));
        Optional<Order> parentOrder = orderRepository.findByOrderNumber(orderNumber);
        if (parentOrder.isPresent()) {
            List<StoreSubOrder> subOrders = storeSubOrderRepository.findByOrderId(parentOrder.get().getId());
            if (!subOrders.isEmpty()) {
                ensurePickupOtp(order);

                List<StoreSubOrder> eligibleSubOrders = subOrders.stream()
                        .filter(subOrder -> subOrder.getStore() != null)
                        .filter(subOrder -> subOrder.getStatus() != StoreSubOrder.SubOrderStatus.STORE_REJECTED)
                        .filter(subOrder -> subOrder.getStatus() != StoreSubOrder.SubOrderStatus.CANCELLED)
                        .filter(subOrder -> subOrder.getStatus() != StoreSubOrder.SubOrderStatus.PICKED_UP)
                        .toList();

                if (eligibleSubOrders.isEmpty()) {
                    throw new IllegalStateException("No eligible store sub-orders found to send OTP");
                }

                for (StoreSubOrder subOrder : eligibleSubOrders) {
                    whatsAppService.sendPickupOtpToStore(order, subOrder.getStore());
                    subOrder.setStoreAcceptedAt(subOrder.getStoreAcceptedAt() != null
                            ? subOrder.getStoreAcceptedAt()
                            : LocalDateTime.now());
                    subOrder.setStatus(StoreSubOrder.SubOrderStatus.OTP_SENT);
                    storeSubOrderRepository.save(subOrder);
                }

                deliveryOrderRepository.save(order);
                return;
            }
        }

        if (order.getStore() == null || order.getStore().getPhoneNumber() == null) {
            throw new IllegalStateException("Store or store phone number not set for this order");
        }
        generateAndSendPickupOtp(order);
        deliveryOrderRepository.save(order);
    }

    private void generateAndSendPickupOtp(DeliveryOrder order) {
        ensurePickupOtp(order);
        order.setStatus(DeliveryOrder.OrderStatus.PICKUP_OTP_GENERATED);
        
        // Notify Store via WhatsApp again with the OTP
        whatsAppService.sendPickupOtpToStore(order);
    }

    private void ensurePickupOtp(DeliveryOrder order) {
        boolean pickupOtpMissing = order.getPickupOtp() == null || order.getPickupOtp().isBlank();
        boolean pickupOtpExpired = order.getPickupOtpExpiry() != null
                && order.getPickupOtpExpiry().isBefore(LocalDateTime.now());

        if (pickupOtpMissing || pickupOtpExpired) {
            String otp = String.format("%04d", (int) (Math.random() * 10000));
            order.setPickupOtp(otp);
            order.setPickupOtpExpiry(LocalDateTime.now().plusHours(1));
        }
    }

    private static final double BROADCAST_RADIUS_KM = 5.0;

    private void broadcastToRiders(DeliveryOrder order) {
        order.setStatus(DeliveryOrder.OrderStatus.BROADCASTED_TO_RIDERS);
        deliveryOrderRepository.save(order);

        // Find active riders within 5km of the store (Haversine) — fall back to all active riders if store has no coords
        List<DeliveryPerson> activeRiders;
        com.project.Anusha.model.Store store = order.getStore();
        if (store != null && store.getLatitude() != null && store.getLongitude() != null) {
            activeRiders = deliveryPersonRepository.findNearbyOnlineRiders(
                    store.getLatitude(), store.getLongitude(), BROADCAST_RADIUS_KM);
        } else {
            activeRiders = deliveryPersonRepository.findByIsOnlineTrueAndIsApprovedByAdminTrueAndIsVerifiedTrue();
        }

        List<UserMain> riderUsers = activeRiders.stream()
            .map(DeliveryPerson::getUserMain)
            .filter(java.util.Objects::nonNull)
            .toList();

        if (!riderUsers.isEmpty()) {
            com.project.Anusha.model.Store s = order.getStore();
            Map<String, String> data = new HashMap<>();
            data.put("type", "NEW_DELIVERY_ORDER");
            data.put("orderId", order.getId().toString());
            data.put("orderNumber", order.getOrderNumber());
            data.put("pickup", order.getPickupAddress());
            data.put("delivery", order.getDeliveryAddress());
            data.put("amount", order.getOrderAmount() != null ? order.getOrderAmount().toString() : "0");
            // Store details so the rider app can display store name, call the store, and open maps
            data.put("storeName", s != null && s.getName() != null ? s.getName() : "");
            data.put("storePhone", s != null && s.getPhoneNumber() != null ? s.getPhoneNumber() : "");
            data.put("storeLatitude", s != null && s.getLatitude() != null ? s.getLatitude().toString() : "");
            data.put("storeLongitude", s != null && s.getLongitude() != null ? s.getLongitude().toString() : "");
            // Earnings / distance
            data.put("deliveryFee", order.getDeliveryFee() != null ? order.getDeliveryFee().toString() : "0");
            data.put("distanceKm", order.getDistanceKm() != null ? order.getDistanceKm().toString() : "");
            data.put("customerName", order.getCustomerName() != null ? order.getCustomerName() : "");

            appNotificationService.notifyUsers(
                riderUsers,
                "NEW_DELIVERY_ORDER",
                "OrderDetail",
                "New Order Available",
                "Earn Rs " + order.getDeliveryFee() + " for a delivery from " + (s != null ? s.getName() : "store"),
                order.getId(),
                order.getOrderNumber(),
                null,
                data);
        }
        
        firebaseService.pushDashboardEvent("BROADCASTED_TO_RIDERS", order.getOrderNumber());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // RIDER WORKFLOW (App)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Atomic 'First-to-Accept' Logic
     */
    public DeliveryOrder acceptOrder(Long orderId, Long deliveryPersonId) {
        int updatedRows = deliveryOrderRepository.updateRiderAssignment(
            orderId, deliveryPersonId, LocalDateTime.now());

        if (updatedRows == 0) {
            DeliveryOrder current = getOrderById(orderId);
            if (current.getDeliveryPerson() != null) {
                throw new IllegalStateException("Order already taken by another rider.");
            }
            throw new IllegalStateException("Order no longer available.");
        }

        DeliveryOrder order = getOrderById(orderId);

        orderRepository.findByOrderNumber(order.getOrderNumber()).ifPresent(customerOrder -> {
            customerOrder.setOrderStatus("assigned");
            orderRepository.save(customerOrder);
        });
        
        // Notify Customer
        String riderName = order.getDeliveryPerson().getFirstName() + " " + order.getDeliveryPerson().getLastName();
        notifyCustomer(order, "ORDER_ASSIGNED", "Rider Assigned",
            riderName + " has accepted your order and is heading to the store.");

        // Dashboard update
        firebaseService.pushDashboardEvent("RIDER_ASSIGNED", Map.of(
            "orderNumber", order.getOrderNumber(),
            "riderName", riderName
        ));

        firebaseService.updateOrderTrackingStatus(order.getOrderNumber(), "RIDER_ASSIGNED");
        
        return order;
    }

    /**
     * Rider reaches store
     */
    public void markArrivedAtStore(String orderNumber) {
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        
        order.setStatus(DeliveryOrder.OrderStatus.REACHED_STORE);
        deliveryOrderRepository.save(order);
        
        firebaseService.updateOrderTrackingStatus(orderNumber, "REACHED_STORE");
    }

    /**
     * Haversine formula — great-circle distance between two GPS points in km.
     */
    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth radius km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Verify pickup OTP input by rider.
     * On success: calculates store→customer distance via Haversine,
     * recalculates deliveryFee using the rider's vehicle FareRule,
     * and advances status to OUT_FOR_DELIVERY.
     */
    public DeliveryOrder verifyPickupOtpAndPickedUp(String orderNumber, String otp) {
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getPickupOtp() == null || !order.getPickupOtp().equals(otp)) {
            throw new IllegalArgumentException("Invalid Pickup OTP");
        }

        if (order.getPickupOtpExpiry() != null && order.getPickupOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP Expired");
        }

        if (order.getPickedUpAt() != null ||
                order.getStatus() == DeliveryOrder.OrderStatus.OUT_FOR_DELIVERY ||
                order.getStatus() == DeliveryOrder.OrderStatus.DELIVERED) {
            return order;
        }

        // ── Distance & fare calculation ──────────────────────────────────────
        Double pLat = order.getPickupLatitude();
        Double pLon = order.getPickupLongitude();
        Double dLat = order.getDeliveryLatitude();
        Double dLon = order.getDeliveryLongitude();

        if (pLat != null && pLon != null && dLat != null && dLon != null) {
            double distanceKm = haversineKm(pLat, pLon, dLat, dLon);
            // Round to 2 decimal places
            distanceKm = Math.round(distanceKm * 100.0) / 100.0;
            order.setDistanceKm(distanceKm);

            // Recalculate fare using rider's vehicle type
            if (order.getDeliveryPerson() != null && order.getDeliveryPerson().getVehicleType() != null) {
                try {
                    java.math.BigDecimal calculatedFare = fareRuleService.calculateDeliveryCharge(
                            order.getDeliveryPerson().getVehicleType(), distanceKm);
                    order.setDeliveryFee(calculatedFare);
                } catch (Exception ignored) {
                    // Keep existing fee if fare rule is missing for this vehicle type
                }
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        order.setStatus(DeliveryOrder.OrderStatus.PICKED_UP);
        order.setPickedUpAt(LocalDateTime.now());

        // Automatically advance to Out for Delivery
        order.setStatus(DeliveryOrder.OrderStatus.OUT_FOR_DELIVERY);

        DeliveryOrder saved = deliveryOrderRepository.save(order);

        if (saved.getDeliveryPerson() != null
                && saved.getDeliveryPerson().getLatitude() != null
                && saved.getDeliveryPerson().getLongitude() != null) {
            firebaseService.updateDeliveryLocation(
                    orderNumber,
                    saved.getDeliveryPerson().getLatitude(),
                    saved.getDeliveryPerson().getLongitude(),
                    saved.getStatus().name(),
                    saved.getDeliveryPerson().getFullName(),
                    saved.getDeliveryPerson().getPhoneNumber()
            );
        } else {
            firebaseService.updateOrderTrackingStatus(orderNumber, "OUT_FOR_DELIVERY");
        }
        notifyCustomer(order, "ORDER_OUT_FOR_DELIVERY", "Out for Delivery",
            "The rider has picked up your order and is on the way. Estimated fare: Rs "
            + (saved.getDeliveryFee() != null ? saved.getDeliveryFee().toPlainString() : "0"));

        return saved;
    }

    /**
     * Final Delivery Confirmation
     */
    public DeliveryOrder confirmOrderDelivered(String orderNumber) {
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        order.setStatus(DeliveryOrder.OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        
        DeliveryOrder saved = deliveryOrderRepository.save(order);

        orderRepository.findByOrderNumber(orderNumber).ifPresent(customerOrder -> {
            customerOrder.setOrderStatus("delivered");
            customerOrder.setDeliveredAt(saved.getDeliveredAt());
            orderRepository.save(customerOrder);
        });
        
        // Update rider earnings
        if (order.getDeliveryPerson() != null && order.getDeliveryFee() != null) {
            deliveryPersonService.updateEarnings(
                order.getDeliveryPerson().getId(), 
                order.getDeliveryFee().doubleValue());
        }

        firebaseService.clearDeliveryTracking(orderNumber);
        firebaseService.pushDashboardEvent("ORDER_DELIVERED", orderNumber);
        
        notifyCustomer(order, "ORDER_DELIVERED", "Delivered", "Enjoy your items!");
        
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    private void notifyCustomer(DeliveryOrder order, String type, String title, String body) {
        if (order == null || order.getCustomerPhone() == null) {
            return;
        }

        userMainService.findByPhoneNumber(order.getCustomerPhone()).ifPresent(um -> {
            Long customerOrderId = orderRepository.findByOrderNumber(order.getOrderNumber())
                    .map(Order::getId)
                    .orElse(null);

            appNotificationService.notifyUser(
                    um,
                    type,
                    "OrderTracking",
                    title,
                    body,
                    customerOrderId,
                    order.getOrderNumber(),
                    null
            );
        });
    }

    private void notifyCustomerOrder(Order order, String type, String title, String body) {
        if (order == null || order.getCustomer() == null || order.getCustomer().getUserMain() == null) {
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

    private void refundWalletIfApplicable(Order order, String description) {
        if (order == null || order.getCustomer() == null || order.getCustomer().getUserMain() == null) {
            return;
        }

        BigDecimal refundAmount = resolveWalletRefundAmount(order);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        walletService.addMoney(
                order.getCustomer().getUserMain().getId(),
                refundAmount,
                description
        );
    }

    private BigDecimal resolveWalletRefundAmount(Order order) {
        String method = order.getPaymentMethod() == null ? "" : order.getPaymentMethod().trim().toUpperCase();
        if ("WALLET".equals(method)) {
            return order.getGrandTotal() != null ? order.getGrandTotal() : java.math.BigDecimal.ZERO;
        }

        if (("COD_WALLET".equals(method) || "ONLINE_WALLET".equals(method))
                && order.getWalletAmount() != null
                && order.getWalletAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            boolean walletWasDeducted = "COD_WALLET".equals(method)
                    || "PAID".equalsIgnoreCase(order.getPaymentStatus());
            if (walletWasDeducted) {
                return order.getWalletAmount();
            }
        }

        return java.math.BigDecimal.ZERO;
    }

    private boolean usesWalletPayment(Order order) {
        String method = order != null && order.getPaymentMethod() != null
                ? order.getPaymentMethod().trim().toUpperCase() : "";
        return "WALLET".equals(method) || "COD_WALLET".equals(method) || "ONLINE_WALLET".equals(method);
    }

    public List<DeliveryOrder> getAllActiveOrders() {
        return deliveryOrderRepository.findAll().stream()
            .filter(o -> o.getStatus() != DeliveryOrder.OrderStatus.DELIVERED &&
                        o.getStatus() != DeliveryOrder.OrderStatus.CANCELLED)
            .toList();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MISSING METHODS REQUIRED BY CONTROLLERS
    // ──────────────────────────────────────────────────────────────────────────

    public List<DeliveryOrder> getOrdersByStatus(DeliveryOrder.OrderStatus status) {
        return deliveryOrderRepository.findByStatus(status);
    }

    public List<DeliveryOrder> getOrdersForDeliveryPerson(Long deliveryPersonId) {
        return deliveryOrderRepository.findByDeliveryPersonIdOrderByCreatedAtDesc(deliveryPersonId);
    }

    public List<DeliveryOrder> getActiveOrdersForDeliveryPerson(Long deliveryPersonId) {
        return deliveryOrderRepository.findByDeliveryPersonId(deliveryPersonId).stream()
            .filter(o -> o.getStatus() != DeliveryOrder.OrderStatus.DELIVERED &&
                        o.getStatus() != DeliveryOrder.OrderStatus.CANCELLED)
            .toList();
    }

    public List<DeliveryOrder> getCompletedOrdersForDeliveryPerson(Long deliveryPersonId) {
        return deliveryOrderRepository.findCompletedOrdersByDeliveryPerson(deliveryPersonId);
    }

    public List<DeliveryOrder> getRecentOrdersForDeliveryPerson(Long deliveryPersonId, int limit) {
        return deliveryOrderRepository.findByDeliveryPersonIdOrderByCreatedAtDesc(deliveryPersonId)
            .stream().limit(limit).toList();
    }

    public boolean verifyPickupOtp(String orderNumber, String otp) {
        return deliveryOrderRepository.findByOrderNumber(orderNumber)
            .map(o -> o.getPickupOtp() != null && o.getPickupOtp().equals(otp) &&
                      (o.getPickupOtpExpiry() == null || o.getPickupOtpExpiry().isAfter(LocalDateTime.now())))
            .orElse(false);
    }

    public DeliveryOrder confirmOrderPickup(String orderNumber, String otp) {
        return verifyPickupOtpAndPickedUp(orderNumber, otp);
    }

    public boolean verifyDeliveryOtp(String orderNumber, String otp) {
        return deliveryOrderRepository.findByOrderNumber(orderNumber)
            .map(o -> {
                boolean pickupCompleted = o.getPickedUpAt() != null
                        || o.getStatus() == DeliveryOrder.OrderStatus.PICKED_UP
                        || o.getStatus() == DeliveryOrder.OrderStatus.OUT_FOR_DELIVERY
                        || o.getStatus() == DeliveryOrder.OrderStatus.DELIVERED;

                return pickupCompleted
                        && o.getDeliveryOtp() != null
                        && o.getDeliveryOtp().equals(otp)
                        && (o.getDeliveryOtpExpiry() == null || o.getDeliveryOtpExpiry().isAfter(LocalDateTime.now()));
            })
            .orElse(false);
    }

    public DeliveryOrder confirmOrderDelivery(String orderNumber, String otp) {
        // Verify delivery OTP first
        if (!verifyDeliveryOtp(orderNumber, otp)) {
            throw new IllegalArgumentException("Invalid or expired delivery OTP");
        }
        return confirmOrderDelivered(orderNumber);
    }

    public Map<String, Object> getOrderStatisticsForDeliveryPerson(Long deliveryPersonId) {
        Map<String, Object> stats = new HashMap<>();
        List<DeliveryOrder> all = deliveryOrderRepository.findByDeliveryPersonId(deliveryPersonId);
        List<DeliveryOrder> completed = all.stream()
            .filter(o -> o.getStatus() == DeliveryOrder.OrderStatus.DELIVERED)
            .collect(java.util.stream.Collectors.toList());

        stats.put("totalOrders", all.size());
        stats.put("completedOrders", completed.size());
        stats.put("cancelledOrders", all.stream().filter(o -> o.getStatus() == DeliveryOrder.OrderStatus.CANCELLED).count());
        stats.put("activeOrders", all.stream().filter(o -> o.getStatus() != DeliveryOrder.OrderStatus.DELIVERED
            && o.getStatus() != DeliveryOrder.OrderStatus.CANCELLED).count());

        // Total earnings from all delivered orders
        double totalEarnings = completed.stream()
            .mapToDouble(o -> o.getDeliveryFee() != null ? o.getDeliveryFee().doubleValue() : 0)
            .sum();
        stats.put("totalEarnings", totalEarnings);

        // Weekly earnings — last 7 days
        java.time.LocalDateTime weekAgo = java.time.LocalDateTime.now().minusDays(7);
        List<DeliveryOrder> weeklyCompleted = completed.stream()
            .filter(o -> o.getDeliveredAt() != null && o.getDeliveredAt().isAfter(weekAgo))
            .collect(java.util.stream.Collectors.toList());
        double weeklyEarnings = weeklyCompleted.stream()
            .mapToDouble(o -> o.getDeliveryFee() != null ? o.getDeliveryFee().doubleValue() : 0)
            .sum();
        stats.put("weeklyEarnings", weeklyEarnings);
        stats.put("weeklyOrders", weeklyCompleted.size());

        // Monthly earnings — last 30 days
        java.time.LocalDateTime monthAgo = java.time.LocalDateTime.now().minusDays(30);
        List<DeliveryOrder> monthlyCompleted = completed.stream()
            .filter(o -> o.getDeliveredAt() != null && o.getDeliveredAt().isAfter(monthAgo))
            .collect(java.util.stream.Collectors.toList());
        double monthlyEarnings = monthlyCompleted.stream()
            .mapToDouble(o -> o.getDeliveryFee() != null ? o.getDeliveryFee().doubleValue() : 0)
            .sum();
        stats.put("monthlyEarnings", monthlyEarnings);
        stats.put("monthlyOrders", monthlyCompleted.size());

        return stats;
    }

    public DeliveryOrder cancelOrder(Long orderId, String reason) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus(DeliveryOrder.OrderStatus.CANCELLED);
        order.setRejectionReason(reason);
        return deliveryOrderRepository.save(order);
    }

    public DeliveryOrder createDeliveryOrder(DeliveryOrder order) {
        if (order.getCreatedAt() == null) order.setCreatedAt(LocalDateTime.now());
        if (order.getUpdatedAt() == null) order.setUpdatedAt(LocalDateTime.now());
        return deliveryOrderRepository.save(order);
    }

    /** Accept an order identified by orderNumber (used by DeliveryOrderController). */
    public DeliveryOrder acceptOrder(String orderNumber, Long deliveryPersonId) {
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));
        return acceptOrder(order.getId(), deliveryPersonId);
    }

    /** Reject an order (delivery person declines it). */
    public DeliveryOrder rejectOrder(String orderNumber, Long deliveryPersonId, String reason) {
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));
        order.setStatus(DeliveryOrder.OrderStatus.RIDER_REJECTED);
        order.setRejectionReason(reason);
        // Re-broadcast
        order.setDeliveryPerson(null);
        order.setStatus(DeliveryOrder.OrderStatus.BROADCASTED_TO_RIDERS);
        return deliveryOrderRepository.save(order);
    }

    /** Update live GPS location — persists to MySQL AND pushes to Firebase RTDB. */
    public void updateLiveLocation(String orderNumber, Long deliveryPersonId, double lat, double lng) {
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));
        if (order.getDeliveryPerson() == null || !order.getDeliveryPerson().getId().equals(deliveryPersonId)) {
            throw new IllegalStateException("You are not the assigned rider for this order");
        }
        // Persist to MySQL for Haversine proximity queries
        deliveryPersonRepository.updateLocation(deliveryPersonId, lat, lng, LocalDateTime.now());
        // Push to Firebase RTDB for real-time customer tracking
        String riderName = order.getDeliveryPerson().getFirstName() + " " + order.getDeliveryPerson().getLastName();
        String riderPhone = order.getDeliveryPerson().getPhoneNumber();
        firebaseService.updateDeliveryLocation(orderNumber, lat, lng, order.getStatus().name(), riderName, riderPhone);
    }

    /** Rider GPS ping when online (no active order). Updates MySQL only. */
    public void updateRiderLocation(Long deliveryPersonId, double lat, double lng) {
        int updated = deliveryPersonRepository.updateLocation(deliveryPersonId, lat, lng, LocalDateTime.now());
        if (updated == 0) {
            throw new IllegalArgumentException("Delivery person not found: " + deliveryPersonId);
        }
    }

    /**
     * Generate customer delivery OTP and send via FCM.
     * Called after rider picks up order (OUT_FOR_DELIVERY state).
     */
    public String generateDeliveryOtp(String orderNumber) {
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));

        if (order.getDeliveryPerson() == null) {
            throw new IllegalArgumentException("Assign a rider before generating delivery OTP");
        }

        if (order.getPickedUpAt() == null &&
                order.getStatus() != DeliveryOrder.OrderStatus.PICKED_UP &&
                order.getStatus() != DeliveryOrder.OrderStatus.OUT_FOR_DELIVERY) {
            throw new IllegalArgumentException("Pickup must be verified before generating delivery OTP");
        }

        if (order.getStatus() == DeliveryOrder.OrderStatus.DELIVERED ||
                order.getStatus() == DeliveryOrder.OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot generate delivery OTP for a completed order");
        }

        String otp = String.format("%04d", (int)(Math.random() * 10000));
        order.setDeliveryOtp(otp);
        order.setDeliveryOtpExpiry(LocalDateTime.now().plusHours(2));
        order.setDeliveryOtpVerifiedAt(null);
        deliveryOrderRepository.save(order);
        // Send OTP to customer via FCM
        notifyCustomer(order, "DELIVERY_OTP", "Your Delivery OTP",
            "Share this OTP with the rider to confirm delivery: " + otp +
            " (valid 2 hours). Order: " + orderNumber);
        return otp;
    }

    /**
     * Confirm delivery with photo URL (Phase 7).
     * Verifies delivery OTP, stores photo URL, updates earnings.
     */
    public DeliveryOrder confirmOrderDeliveryWithPhoto(String orderNumber, String otp, String photoUrl) {
        if (!verifyDeliveryOtp(orderNumber, otp)) {
            throw new IllegalArgumentException("Invalid or expired delivery OTP");
        }
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));

        if (order.getPickedUpAt() == null &&
                order.getStatus() != DeliveryOrder.OrderStatus.PICKED_UP &&
                order.getStatus() != DeliveryOrder.OrderStatus.OUT_FOR_DELIVERY) {
            throw new IllegalArgumentException("Pickup must be verified before confirming delivery");
        }

        order.setDeliveryPhotoUrl(photoUrl);
        order.setDeliveryOtpVerifiedAt(LocalDateTime.now());
        deliveryOrderRepository.save(order);
        return confirmOrderDelivered(orderNumber);
    }

    /**
     * Save a 1–5 star rating (plus optional feedback) for a completed delivery.
     * Also updates the delivery person's rolling average rating.
     */
    @org.springframework.transaction.annotation.Transactional
    public DeliveryOrder rateDelivery(String orderNumber, int stars, String feedback) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        DeliveryOrder order = deliveryOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));

        if (order.getStatus() != DeliveryOrder.OrderStatus.DELIVERED) {
            throw new IllegalStateException("Can only rate a delivered order");
        }
        if (order.getCustomerRating() != null) {
            throw new IllegalStateException("This order has already been rated");
        }

        order.setCustomerRating(stars);
        if (feedback != null && !feedback.isBlank()) {
            order.setCustomerFeedback(feedback.trim());
        }
        deliveryOrderRepository.save(order);

        // Update rider's aggregate rating
        if (order.getDeliveryPerson() != null) {
            DeliveryPerson rider = order.getDeliveryPerson();
            List<DeliveryOrder> ratedOrders = deliveryOrderRepository
                    .findByDeliveryPersonIdAndCustomerRatingIsNotNull(rider.getId());
            if (!ratedOrders.isEmpty()) {
                double avg = ratedOrders.stream()
                        .mapToInt(DeliveryOrder::getCustomerRating)
                        .average()
                        .orElse(stars);
                rider.setAverageRating(Math.round(avg * 10.0) / 10.0);
                rider.setTotalRatings(ratedOrders.size());
                deliveryPersonRepository.save(rider);
            }
        }

        return order;
    }
}
