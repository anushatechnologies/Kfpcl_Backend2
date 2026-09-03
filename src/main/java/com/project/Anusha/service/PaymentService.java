package com.project.Anusha.service;

import com.project.Anusha.dto.PaymentRefundResponse;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Order;
import com.project.Anusha.model.OrderItem;
import com.project.Anusha.model.PaymentTransaction;
import com.project.Anusha.repository.OrderRepository;
import com.project.Anusha.repository.PaymentTransactionRepository;
import com.project.Anusha.repository.VariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final CheckoutSettingsService checkoutSettingsService;
    private final WalletService walletService;
    private final VariantRepository variantRepository;
    private final StoreOrderDispatchService storeOrderDispatchService;

    @Transactional(readOnly = true)
    public PaymentRefundResponse getRefundStatus(Long orderId, Customer customer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (customer != null && !order.getCustomer().equals(customer)) {
            throw new RuntimeException("Order does not belong to this customer");
        }
        PaymentTransaction tx = findSuccessfulTransaction(orderId).orElse(null);
        return buildRefundResponse(order, tx, "Refund status fetched");
    }

    @Transactional
    public PaymentRefundResponse processRefund(Long orderId, Customer customer, BigDecimal requestedAmount, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (customer != null && !order.getCustomer().equals(customer)) {
            throw new RuntimeException("Order does not belong to this customer");
        }
        if (customer != null && !isCustomerRefundAllowed(order)) {
            throw new RuntimeException("Please cancel the order before requesting refund");
        }
        return processRefund(order, requestedAmount, reason);
    }

    @Transactional
    public PaymentRefundResponse refundForOrderIfEligible(Order order, String reason) {
        if (order == null || !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return buildRefundResponse(order, null, "Order is not eligible for refund");
        }
        return processRefund(order, null, reason);
    }

    private PaymentRefundResponse processRefund(Order order, BigDecimal requestedAmount, String reason) {
        if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new RuntimeException("Only paid orders can be refunded");
        }

        PaymentTransaction tx = findSuccessfulTransaction(order.getId()).orElse(null);

        BigDecimal amount = requestedAmount != null && requestedAmount.compareTo(BigDecimal.ZERO) > 0
                ? requestedAmount
                : (tx != null ? tx.getAmount() : order.getGrandTotal());

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Refund amount must be greater than zero");
        }

        if (tx != null) {
            tx.setRefundId("RFND_" + order.getId() + "_" + System.currentTimeMillis());
            tx.setRefundAmount(amount);
            tx.setRefundStatus("SUCCESS");
            tx.setRefundReason(reason != null && !reason.isBlank() ? reason.trim() : "Refund processed");
            tx.setRefundedAt(LocalDateTime.now());
            paymentTransactionRepository.save(tx);
        }

        if (tx != null && tx.getAmount() != null && amount.compareTo(tx.getAmount()) < 0) {
            order.setPaymentStatus("PARTIALLY_REFUNDED");
        } else {
            order.setPaymentStatus("REFUNDED");
        }
        orderRepository.save(order);

        return buildRefundResponse(order, tx, "Refund processed successfully");
    }

    private java.util.Optional<PaymentTransaction> findSuccessfulTransaction(Long orderId) {
        return paymentTransactionRepository.findTopByOrderIdAndStatusAndPaymentMethodOrderByCreatedAtDesc(
                orderId, "SUCCESS", "ONLINE");
    }

    public void markOrderPaymentFailedAndRestoreStock(Order order) {
        if (order == null) {
            return;
        }

        String currentOrderStatus = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim();
        if ("PAYMENT_FAILED".equalsIgnoreCase(currentOrderStatus)
                || "cancelled".equalsIgnoreCase(currentOrderStatus)
                || "delivered".equalsIgnoreCase(currentOrderStatus)) {
            return;
        }

        order.setPaymentStatus("FAILED");
        order.setOrderStatus("PAYMENT_FAILED");
        restoreStockForOrder(order);
        orderRepository.save(order);
    }

    public void restoreStockForOrder(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
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

    public void applyWalletDeductionForOnlineWallet(Order order) {
        if (!"ONLINE_WALLET".equalsIgnoreCase(order.getPaymentMethod())
                || order.getWalletAmount() == null
                || order.getWalletAmount().compareTo(BigDecimal.ZERO) <= 0
                || order.getCustomer() == null
                || order.getCustomer().getUserMain() == null) {
            return;
        }

        try {
            walletService.deductMoney(order.getCustomer().getUserMain().getId(),
                    order.getWalletAmount(),
                    "Wallet portion for order " + order.getOrderNumber());
        } catch (Exception e) {
            log.warn("Wallet deduction failed for order {}: {}", order.getId(), e.getMessage());
        }
    }

    private boolean isCustomerRefundAllowed(Order order) {
        String status = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim().toLowerCase();
        return "cancelled".equals(status) || "rejected".equals(status);
    }

    private PaymentRefundResponse buildRefundResponse(Order order, PaymentTransaction tx, String message) {
        if (order == null) {
            return new PaymentRefundResponse(false, null, null, null, null,
                    "NOT_ELIGIBLE", null, BigDecimal.ZERO, null, null, message);
        }
        return new PaymentRefundResponse(
                tx != null && tx.getRefundStatus() != null && !"FAILED".equalsIgnoreCase(tx.getRefundStatus()),
                order.getId(),
                order.getOrderNumber(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                tx != null && tx.getRefundStatus() != null ? tx.getRefundStatus() : "NOT_REQUESTED",
                tx != null ? tx.getRefundId() : null,
                tx != null ? tx.getRefundAmount() : BigDecimal.ZERO,
                tx != null ? tx.getRefundReason() : null,
                tx != null ? tx.getRefundedAt() : null,
                message);
    }
}
