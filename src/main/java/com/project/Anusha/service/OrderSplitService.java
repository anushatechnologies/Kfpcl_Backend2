package com.project.Anusha.service;

import com.project.Anusha.model.*;
import com.project.Anusha.repository.OrderItemRepository;
import com.project.Anusha.repository.StoreSubOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 4 — Multi-store order splitting.
 * Groups order items by their store and creates StoreSubOrder per store.
 * Sends WhatsApp OTP to each store for pickup verification.
 */
@Service
public class OrderSplitService {

    @Autowired
    private StoreSubOrderRepository storeSubOrderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private StoreOrderDispatchService storeOrderDispatchService;

    /**
     * Split a customer order into per-store sub-orders and notify each store via WhatsApp.
     * Returns the list of created sub-orders.
     */
    @Transactional
    public List<StoreSubOrder> splitAndNotify(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

        Map<Store, List<OrderItem>> byStore = items.stream()
                .filter(item -> item.getVariant() != null &&
                                item.getVariant().getProduct() != null &&
                                item.getVariant().getProduct().getStore() != null)
                .collect(Collectors.groupingBy(item -> item.getVariant().getProduct().getStore()));

        List<StoreSubOrder> subOrders = new ArrayList<>();
        String customerName = order.getCustomer() != null ? order.getCustomer().getName() : "Customer";

        for (Map.Entry<Store, List<OrderItem>> entry : byStore.entrySet()) {
            Store store = entry.getKey();
            List<OrderItem> storeItems = entry.getValue();

            String summary = storeItems.stream()
                    .map(i -> i.getProductName() + " x" + i.getQuantity())
                    .collect(Collectors.joining(", "));

            BigDecimal subtotal = storeItems.stream()
                    .map(i -> i.getTotalPrice() != null ? i.getTotalPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            StoreSubOrder sub = storeSubOrderRepository.findByOrderIdAndStoreId(order.getId(), store.getId())
                    .orElseGet(StoreSubOrder::new);
            sub.setOrder(order);
            sub.setStore(store);
            sub.setPickupOtp(sub.getPickupOtp() != null ? sub.getPickupOtp()
                    : String.format("%04d", (int) (Math.random() * 10000)));
            sub.setOtpExpiry(LocalDateTime.now().plusHours(3));
            sub.setItemSummary(summary);
            sub.setSubtotal(subtotal);
            sub.setStatus(StoreSubOrder.SubOrderStatus.STORE_NOTIFIED);
            sub.setStoreNotifiedAt(LocalDateTime.now());
            storeSubOrderRepository.save(sub);

            notifyStore(store, order, summary, subtotal, customerName);

            subOrders.add(sub);
        }

        System.out.println("[ORDER_SPLIT] Order " + order.getOrderNumber() +
                " split into " + subOrders.size() + " sub-orders");
        return subOrders;
    }

    /**
     * Handle store acceptance of a sub-order.
     */
    @Transactional
    public StoreSubOrder acceptSubOrder(Long subOrderId) {
        StoreSubOrder sub = storeSubOrderRepository.findById(subOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-order not found: " + subOrderId));
        sub.setStatus(StoreSubOrder.SubOrderStatus.STORE_ACCEPTED);
        sub.setStoreAcceptedAt(LocalDateTime.now());
        return storeSubOrderRepository.save(sub);
    }

    /**
     * Verify store pickup OTP (rider picks up items from this specific store).
     */
    @Transactional
    public boolean verifyStorePickupOtp(Long subOrderId, String otp) {
        StoreSubOrder sub = storeSubOrderRepository.findById(subOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-order not found: " + subOrderId));
        if (sub.getPickupOtp() == null || !sub.getPickupOtp().equals(otp)) {
            return false;
        }
        if (sub.getOtpExpiry() != null && sub.getOtpExpiry().isBefore(LocalDateTime.now())) {
            return false;
        }
        sub.setStatus(StoreSubOrder.SubOrderStatus.PICKED_UP);
        sub.setPickedUpAt(LocalDateTime.now());
        storeSubOrderRepository.save(sub);
        return true;
    }

    /** Returns all sub-orders for an order. */
    public List<StoreSubOrder> getSubOrdersForOrder(Long orderId) {
        return storeSubOrderRepository.findByOrderId(orderId);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void notifyStore(Store store, Order order, String itemSummary,
                             BigDecimal subtotal, String customerName) {
        String storePhone = store.getPhoneNumber();
        if (storePhone == null || storePhone.isBlank()) {
            System.err.println("[ORDER_SPLIT] Store " + store.getName() + " has no phone number — skipping WhatsApp");
            return;
        }
        try {
            storeOrderDispatchService.notifyStoreOfOrderItems(
                    store,
                    order,
                    itemSummary,
                    subtotal,
                    customerName);
        } catch (Exception e) {
            System.err.println("[ORDER_SPLIT] WhatsApp notification failed for store " + store.getName() + ": " + e.getMessage());
        }
    }
}
