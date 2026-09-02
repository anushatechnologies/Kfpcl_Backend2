package com.project.Anusha.controller;

import com.project.Anusha.config.CashfreeConfig;
import com.project.Anusha.dto.PaymentInitiateRequest;
import com.project.Anusha.dto.PaymentRefundRequest;
import com.project.Anusha.dto.CashfreeOrderResponse;
import com.project.Anusha.dto.CashfreeVerifyRequest;
import com.project.Anusha.model.Customer;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;
    private final CustomerService customerService;
    private final CashfreeConfig cashfreeConfig;

    private Customer getCustomer(UserDetails userDetails) {
        return customerService.getCustomerByPhone(userDetails.getUsername());
    }

    /**
     * Step 1 — Customer initiates payment for an existing order.
     *
     * POST /api/payment/initiate
     * Body: { "orderId": 123 }
     *
     * Response: { cfOrderId, paymentSessionId, amountInPaise, currency, receipt, environment }
     *
     * Frontend uses paymentSessionId and environment to open Cashfree checkout SDK.
     */
    @PostMapping("/initiate")
    public ResponseEntity<?> initiatePayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PaymentInitiateRequest request) {
        Customer customer = getCustomer(userDetails);
        CashfreeOrderResponse response = paymentService.initiatePayment(request.getOrderId(), customer, request.getWalletAmount());
        return ResponseEntity.ok(response);
    }

    /**
     * Step 2 — Frontend calls this after Cashfree checkout completes/redirects.
     * Backend verifies the transaction status with Cashfree servers.
     *
     * POST /api/payment/verify
     * Body: { orderId, cfOrderId, receipt }
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody CashfreeVerifyRequest request) {
        paymentService.verifyAndMarkPaid(
                request.getCfOrderId(),
                request.getReceipt());
        return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Payment verified successfully"));
    }

    @PostMapping("/refund/request")
    public ResponseEntity<?> requestRefund(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PaymentRefundRequest request) {
        Customer customer = getCustomer(userDetails);
        return ResponseEntity.ok(paymentService.processRefund(
                request.getOrderId(),
                customer,
                request.getAmount(),
                request.getReason()));
    }

    @GetMapping("/refund-status/{orderId}")
    public ResponseEntity<?> getRefundStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId) {
        Customer customer = getCustomer(userDetails);
        return ResponseEntity.ok(paymentService.getRefundStatus(orderId, customer));
    }

    /**
     * Step 3 (server-side fallback) — Cashfree webhook.
     * Cashfree POSTs JSON to this endpoint for success / failure events.
     *
     * POST /api/payment/webhook   ← must be PUBLIC in SecurityConfig
     * Headers: x-webhook-signature, x-webhook-timestamp
     * Body: raw JSON string
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "x-webhook-signature", required = false) String signature,
            @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp) {
        try {
            paymentService.handleWebhook(rawBody, timestamp, signature);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
