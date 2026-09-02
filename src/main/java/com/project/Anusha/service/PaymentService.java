package com.project.Anusha.service;

import com.project.Anusha.config.CashfreeConfig;
import com.project.Anusha.dto.PaymentRefundResponse;
import com.project.Anusha.dto.CashfreeOrderResponse;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Order;
import com.project.Anusha.model.OrderItem;
import com.project.Anusha.model.PaymentTransaction;
import com.project.Anusha.repository.OrderRepository;
import com.project.Anusha.repository.PaymentTransactionRepository;
import com.project.Anusha.repository.VariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final CashfreeService cashfreeService;
    private final CashfreeConfig cashfreeConfig;
    private final CheckoutSettingsService checkoutSettingsService;
    private final WalletService walletService;
    private final VariantRepository variantRepository;
    private final StoreOrderDispatchService storeOrderDispatchService;

    /**
     * Creates a Cashfree order for the given internal order.
     * Returns everything the frontend needs to open Cashfree SDK checkout.
     */
    @Transactional
    public CashfreeOrderResponse initiatePayment(Long orderId, Customer customer) {
        return initiatePayment(orderId, customer, null);
    }

    @Transactional
    public CashfreeOrderResponse initiatePayment(Long orderId, Customer customer, BigDecimal walletAmount) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getCustomer().equals(customer)) {
            throw new RuntimeException("Order does not belong to this customer");
        }
        if (!"ONLINE".equalsIgnoreCase(order.getPaymentMethod())
                && !"ONLINE_WALLET".equalsIgnoreCase(order.getPaymentMethod())) {
            throw new RuntimeException("This order is not eligible for online payment");
        }
        if (!checkoutSettingsService.isOnlinePaymentEnabled()) {
            throw new RuntimeException("Online payment is currently disabled by admin");
        }
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new RuntimeException("Order is already paid");
        }

        // For ONLINE_WALLET: use the walletAmount stored on the order (set at placement time)
        BigDecimal walletCapped = BigDecimal.ZERO;
        if ("ONLINE_WALLET".equalsIgnoreCase(order.getPaymentMethod())
                && order.getWalletAmount() != null
                && order.getWalletAmount().compareTo(BigDecimal.ZERO) > 0) {
            walletCapped = order.getWalletAmount();
        }
        BigDecimal effectiveCharge = order.getGrandTotal().subtract(walletCapped);

        // Zero-amount orders (free / full coupon discount, or wallet covers everything)
        if (effectiveCharge.compareTo(BigDecimal.ZERO) <= 0) {
            // Wallet covers everything — deduct wallet and mark paid
            if (walletCapped.compareTo(BigDecimal.ZERO) > 0 && order.getCustomer().getUserMain() != null) {
                walletService.deductMoney(order.getCustomer().getUserMain().getId(),
                        walletCapped, "Wallet payment for order " + order.getOrderNumber());
            }
            order.setPaymentStatus("PAID");
            order.setOrderStatus("confirmed");
            orderRepository.save(order);
            storeOrderDispatchService.notifyOperatorOfPaidOrder(order);
            log.info("Zero-amount order {} auto-marked PAID (walletCapped={})", orderId, walletCapped);
            // environment placeholder is safe to be mock for free checkout
            return new CashfreeOrderResponse("FREE_ORDER", "FREE", 0, "INR", "FREE", "sandbox");
        }

        // Unique receipt id (our internal txnid)
        String receipt = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        // Create Cashfree order for effectiveCharge (grandTotal - walletCapped)
        Map<String, Object> cfOrder;
        try {
            cfOrder = cashfreeService.createOrder(order, receipt, effectiveCharge);
        } catch (Exception e) {
            log.error("Cashfree order creation failed for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Payment initiation failed: " + e.getMessage(), e);
        }

        String cfOrderId = (String) cfOrder.get("order_id");
        String paymentSessionId = (String) cfOrder.get("payment_session_id");
        String currency = (String) cfOrder.get("order_currency");
        
        // Cashfree amount is returned as Double/Float/String/BigDecimal. Make it safe
        BigDecimal orderAmount = BigDecimal.ZERO;
        Object rawAmount = cfOrder.get("order_amount");
        if (rawAmount instanceof Number) {
            orderAmount = BigDecimal.valueOf(((Number) rawAmount).doubleValue());
        } else if (rawAmount instanceof String) {
            orderAmount = new BigDecimal((String) rawAmount);
        }

        long amountInPaise = orderAmount.multiply(BigDecimal.valueOf(100)).longValue();

        // Persist pending transaction
        PaymentTransaction tx = new PaymentTransaction();
        tx.setOrder(order);
        tx.setPaymentMethod("CASHFREE");
        tx.setAmount(effectiveCharge);
        tx.setStatus("PENDING");
        tx.setTransactionId(receipt);                // our receipt / txnid
        tx.setGatewayOrderId(cfOrderId);             // Cashfree order ID
        tx.setGatewayResponse(new JSONObject(cfOrder).toString()); // store full Cashfree order JSON
        paymentTransactionRepository.save(tx);

        log.info("Payment initiated for order {} | cf_order_id={} | receipt={}", orderId, cfOrderId, receipt);

        // Figure out environment type based on API URL configured
        String env = cashfreeConfig.getApiUrl().contains("sandbox") ? "sandbox" : "production";

        return new CashfreeOrderResponse(cfOrderId, paymentSessionId, amountInPaise, currency, receipt, env);
    }

    /**
     * Called after the customer completes payment in Cashfree checkout.
     * Verifies payment status with Cashfree servers, then marks order PAID.
     */
    @Transactional
    public void verifyAndMarkPaid(String cfOrderId, String receipt) {
        boolean valid = cashfreeService.verifyPayment(cfOrderId);
        if (!valid) {
            throw new RuntimeException("Invalid payment - Cashfree did not return SUCCESS status for this order");
        }

        PaymentTransaction tx = paymentTransactionRepository.findByTransactionId(receipt)
                .orElseThrow(() -> new RuntimeException("Transaction not found for receipt: " + receipt));

        if ("SUCCESS".equalsIgnoreCase(tx.getStatus())) {
            // The payment may have been persisted before a WhatsApp send failed.
            // Retry the idempotent notification on repeated verify calls.
            storeOrderDispatchService.notifyOperatorOfPaidOrder(tx.getOrder());
            log.info("Payment verification: transaction {} already marked SUCCESS", receipt);
            return;
        }

        tx.setStatus("SUCCESS");
        tx.setGatewayOrderId(cfOrderId);
        tx.setPaymentDate(LocalDateTime.now());
        paymentTransactionRepository.save(tx);

        Order order = tx.getOrder();
        order.setPaymentStatus("PAID");
        order.setOrderStatus("confirmed");
        orderRepository.save(order);
        applyWalletDeductionForOnlineWallet(order);
        storeOrderDispatchService.notifyOperatorOfPaidOrder(order);

        log.info("Payment verified and order {} marked PAID | cf_order_id={}", order.getId(), cfOrderId);
    }

    /**
     * Cashfree webhook handler (POST /api/payment/webhook).
     * Cashfree sends JSON body with event type and payment details.
     */
    @Transactional
    public void handleWebhook(String rawBody, String timestamp, String signature) {
        // Verify webhook signature if secret is configured
        if (signature != null && !signature.isBlank()) {
            boolean valid = cashfreeService.verifyWebhookSignature(rawBody, timestamp, signature);
            if (!valid) {
                log.warn("Webhook signature invalid — ignoring event");
                throw new RuntimeException("Invalid webhook signature");
            }
        }

        JSONObject payload = new JSONObject(rawBody);
        String event = payload.optString("type");
        log.info("Cashfree webhook event: {}", event);

        // We handle payment success/failure webhooks
        if ("PAYMENT_SUCCESS_WEBHOOK".equals(event)) {
            JSONObject data = payload.optJSONObject("data");
            if (data == null) return;
            
            JSONObject orderEntity = data.optJSONObject("order");
            JSONObject paymentEntity = data.optJSONObject("payment");
            if (orderEntity == null || paymentEntity == null) return;

            String receipt = orderEntity.optString("order_id"); // our TXN...
            String cfPaymentId = paymentEntity.optString("cf_payment_id");
            String paymentStatus = paymentEntity.optString("payment_status");

            if (receipt.isBlank()) {
                log.warn("Webhook: no order_id on payload");
                return;
            }

            if (!"SUCCESS".equalsIgnoreCase(paymentStatus)) {
                log.warn("Webhook: payment status is {} instead of SUCCESS", paymentStatus);
                return;
            }

            if (receipt.startsWith("WALLET_")) {
                handleWalletTopupWebhook(orderEntity, receipt);
                return;
            }

            paymentTransactionRepository.findByTransactionId(receipt).ifPresent(tx -> {
                if ("SUCCESS".equals(tx.getStatus())) {
                    storeOrderDispatchService.notifyOperatorOfPaidOrder(tx.getOrder());
                    log.info("Webhook: transaction {} already marked SUCCESS", receipt);
                    return;
                }
                tx.setStatus("SUCCESS");
                tx.setGatewayPaymentId(cfPaymentId);
                tx.setGatewayResponse(payload.toString());
                tx.setPaymentDate(LocalDateTime.now());
                paymentTransactionRepository.save(tx);

                Order order = tx.getOrder();
                order.setPaymentStatus("PAID");
                order.setOrderStatus("confirmed");
                orderRepository.save(order);
                applyWalletDeductionForOnlineWallet(order);
                storeOrderDispatchService.notifyOperatorOfPaidOrder(order);
                log.info("Webhook: order {} marked PAID via event PAYMENT_SUCCESS_WEBHOOK", order.getId());
            });

        } else if ("PAYMENT_FAILED_WEBHOOK".equals(event)) {
            JSONObject data = payload.optJSONObject("data");
            if (data == null) return;

            JSONObject orderEntity = data.optJSONObject("order");
            JSONObject paymentEntity = data.optJSONObject("payment");
            if (orderEntity == null || paymentEntity == null) return;

            String receipt = orderEntity.optString("order_id");

            paymentTransactionRepository.findByTransactionId(receipt).ifPresent(tx -> {
                tx.setStatus("FAILED");
                tx.setGatewayResponse(payload.toString());
                paymentTransactionRepository.save(tx);
                markOrderPaymentFailedAndRestoreStock(tx.getOrder());
                log.info("Webhook: transaction {} marked FAILED via event PAYMENT_FAILED_WEBHOOK", receipt);
            });
        }
    }

    private void markOrderPaymentFailedAndRestoreStock(Order order) {
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

    private void restoreStockForOrder(Order order) {
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

    @Transactional(readOnly = true)
    public PaymentRefundResponse getRefundStatus(Long orderId, Customer customer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (customer != null && !order.getCustomer().equals(customer)) {
            throw new RuntimeException("Order does not belong to this customer");
        }
        PaymentTransaction tx = findSuccessfulCashfreeTransaction(orderId).orElse(null);
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
        if (order == null || !isOnlinePayment(order) || !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return buildRefundResponse(order, null, "Order is not eligible for Cashfree refund");
        }
        return processRefund(order, null, reason);
    }

    private PaymentRefundResponse processRefund(Order order, BigDecimal requestedAmount, String reason) {
        if (!isOnlinePayment(order)) {
            throw new RuntimeException("Only Cashfree online payments can be refunded through this API");
        }
        if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new RuntimeException("Only paid orders can be refunded");
        }

        PaymentTransaction tx = findSuccessfulCashfreeTransaction(order.getId())
                .orElseThrow(() -> new RuntimeException("Successful Cashfree transaction not found for this order"));

        if (tx.getRefundStatus() != null && !"FAILED".equalsIgnoreCase(tx.getRefundStatus())) {
            return buildRefundResponse(order, tx, "Refund already requested");
        }

        BigDecimal amount = requestedAmount != null && requestedAmount.compareTo(BigDecimal.ZERO) > 0
                ? requestedAmount
                : tx.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Refund amount must be greater than zero");
        }
        if (tx.getAmount() != null && amount.compareTo(tx.getAmount()) > 0) {
            throw new RuntimeException("Refund amount cannot be greater than Cashfree paid amount");
        }

        String cfOrderId = tx.getGatewayOrderId();
        if (cfOrderId == null || cfOrderId.isBlank()) {
            // Fallback to internal txnid if gatewayOrderId is blank
            cfOrderId = tx.getTransactionId();
        }

        try {
            Map<String, Object> refund = cashfreeService.refundPayment(cfOrderId, amount, reason);
            tx.setRefundId((String) refund.get("refund_id"));
            tx.setRefundAmount(amount);
            tx.setRefundStatus(((String) refund.getOrDefault("refund_status", "SUCCESS")).toUpperCase());
            tx.setRefundReason(reason != null && !reason.isBlank() ? reason.trim() : "Refund requested");
            tx.setRefundedAt(LocalDateTime.now());
            tx.setGatewayResponse(new JSONObject(refund).toString());
            paymentTransactionRepository.save(tx);

            if (tx.getAmount() != null && amount.compareTo(tx.getAmount()) < 0) {
                order.setPaymentStatus("PARTIALLY_REFUNDED");
            } else {
                order.setPaymentStatus("REFUNDED");
            }
            orderRepository.save(order);

            return buildRefundResponse(order, tx, "Refund processed successfully");
        } catch (Exception e) {
            tx.setRefundAmount(amount);
            tx.setRefundStatus("FAILED");
            tx.setRefundReason(e.getMessage());
            tx.setRefundedAt(LocalDateTime.now());
            paymentTransactionRepository.save(tx);
            throw new RuntimeException("Refund failed: " + e.getMessage(), e);
        }
    }

    private java.util.Optional<PaymentTransaction> findSuccessfulCashfreeTransaction(Long orderId) {
        return paymentTransactionRepository.findTopByOrderIdAndStatusAndPaymentMethodOrderByCreatedAtDesc(
                orderId, "SUCCESS", "CASHFREE");
    }

    private boolean isOnlinePayment(Order order) {
        String method = order.getPaymentMethod() == null ? "" : order.getPaymentMethod().toUpperCase();
        return "ONLINE".equals(method) || "ONLINE_WALLET".equals(method);
    }

    private void applyWalletDeductionForOnlineWallet(Order order) {
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
            log.warn("Wallet deduction failed after payment for order {}: {}", order.getId(), e.getMessage());
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

    private void handleWalletTopupWebhook(JSONObject orderEntity, String receipt) {
        try {
            Long userMainId = null;
            JSONObject orderTags = orderEntity.optJSONObject("order_tags");
            if (orderTags != null && orderTags.has("userMainId")) {
                userMainId = Long.parseLong(orderTags.getString("userMainId"));
            } else {
                String[] parts = receipt.split("_");
                if (parts.length >= 2) {
                    userMainId = Long.parseLong(parts[1]);
                }
            }

            if (userMainId == null) {
                log.warn("Webhook: Could not determine userMainId from wallet top-up receipt: {}", receipt);
                return;
            }

            if (walletService.isTopupAlreadyCredited(userMainId, receipt)) {
                log.info("Webhook: Wallet top-up {} already credited for userMainId: {}", receipt, userMainId);
                return;
            }

            BigDecimal amount = BigDecimal.ZERO;
            Object rawAmount = orderEntity.opt("order_amount");
            if (rawAmount instanceof Number) {
                amount = BigDecimal.valueOf(((Number) rawAmount).doubleValue());
            } else if (rawAmount instanceof String) {
                amount = new BigDecimal((String) rawAmount);
            }

            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                walletService.addMoney(userMainId, amount, "Wallet Top-up via Cashfree (" + receipt + ")");
                log.info("Webhook: Credited {} to wallet for userMainId: {} via {}", amount, userMainId, receipt);
            }
        } catch (Exception e) {
            log.error("Webhook: Failed to process wallet top-up for receipt {}: {}", receipt, e.getMessage(), e);
        }
    }
}
