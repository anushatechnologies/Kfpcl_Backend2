package com.project.Anusha.controller;

import com.project.Anusha.config.CashfreeConfig;
import com.project.Anusha.dto.CashfreeOrderResponse;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.WalletTransaction;
import com.project.Anusha.service.CashfreeService;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@CrossOrigin(origins = "*")
public class WalletController {

    @Autowired
    private WalletService walletService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CashfreeService cashfreeService;

    @Autowired
    private CashfreeConfig cashfreeConfig;

    /**
     * POST /api/wallet/initiate
     * Initiates a Cashfree payment session specifically for wallet top-up / recharge.
     * Body: { "amount": 100.00 }
     */
    @PostMapping({"/initiate", "/initiate-topup", "/recharge/initiate"})
    public ResponseEntity<?> initiateWalletTopUp(@AuthenticationPrincipal UserDetails userDetails,
                                                 @RequestBody Map<String, Object> request) {
        if (request == null || !request.containsKey("amount") || request.get("amount") == null) {
            throw new IllegalArgumentException("Required parameter 'amount' is missing");
        }

        Customer customer = getCurrentCustomer(userDetails);
        Long userMainId = customer.getUserMain().getId();

        BigDecimal amount = new BigDecimal(request.get("amount").toString().trim());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        String receipt = "WALLET_" + userMainId + "_" + System.currentTimeMillis();
        Map<String, Object> cfOrder = cashfreeService.createWalletTopupOrder(customer, userMainId, amount, receipt);

        String cfOrderId = (String) cfOrder.get("order_id");
        String paymentSessionId = (String) cfOrder.get("payment_session_id");
        String currency = (String) cfOrder.getOrDefault("order_currency", "INR");
        String env = cashfreeConfig.getApiUrl() != null && cashfreeConfig.getApiUrl().contains("sandbox")
                ? "sandbox" : "production";

        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();

        CashfreeOrderResponse response = new CashfreeOrderResponse(
                cfOrderId,
                paymentSessionId,
                amountInPaise,
                currency,
                receipt,
                env
        );

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/wallet/verify
     * Verifies payment with Cashfree after checkout completion and credits user's wallet.
     * Body: { "cfOrderId": "WALLET_...", "amount": 100.00 }
     */
    @PostMapping({"/verify", "/verify-topup", "/recharge/verify"})
    public ResponseEntity<?> verifyWalletTopUp(@AuthenticationPrincipal UserDetails userDetails,
                                               @RequestBody Map<String, Object> request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }

        String cfOrderId = (String) request.get("cfOrderId");
        if (cfOrderId == null || cfOrderId.isBlank()) {
            cfOrderId = (String) request.get("orderId");
        }
        if (cfOrderId == null || cfOrderId.isBlank()) {
            cfOrderId = (String) request.get("receipt");
        }
        if (cfOrderId == null || cfOrderId.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter 'cfOrderId'");
        }

        Customer customer = getCurrentCustomer(userDetails);
        Long userMainId = customer.getUserMain().getId();

        // Check if already credited (idempotency guard)
        if (walletService.isTopupAlreadyCredited(userMainId, cfOrderId)) {
            BigDecimal balance = walletService.getBalance(userMainId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Wallet top-up already verified and credited",
                    "balance", balance
            ));
        }

        boolean isValid = cashfreeService.verifyPayment(cfOrderId);
        if (!isValid) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Payment verification failed with Cashfree"
            ));
        }

        Object rawAmount = request.get("amount");
        BigDecimal amount = BigDecimal.ZERO;
        if (rawAmount != null) {
            amount = new BigDecimal(rawAmount.toString().trim());
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive");
        }

        walletService.addMoney(userMainId, amount, "Wallet Top-up via Cashfree (" + cfOrderId + ")");
        BigDecimal currentBalance = walletService.getBalance(userMainId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Wallet top-up verified and credited successfully",
                "balance", currentBalance
        ));
    }

    /**
     * POST /api/wallet/debit  (also accepts /spend and /deduct for frontend compatibility)
     * Deducts money from the authenticated customer's wallet.
     * Body: { "userMainId": 123, "amount": 50.00, "description": "..." }
     */
    @PostMapping({"/debit", "/spend", "/deduct"})
    public ResponseEntity<?> debitMoney(@AuthenticationPrincipal UserDetails userDetails,
                                        @RequestBody Map<String, Object> request) {
        Number rawId = (Number) request.get("userMainId");
        if (rawId == null) rawId = (Number) request.get("customerId");
        if (rawId == null) rawId = (Number) request.get("userId");
        Long userMainId = rawId != null
                ? resolveWalletOwnerId(userDetails, rawId.longValue())
                : getCurrentCustomer(userDetails).getUserMain().getId();
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String description = (String) request.getOrDefault("description", "Wallet payment");
        walletService.deductMoney(userMainId, amount, description);
        return ResponseEntity.ok(Map.of("success", true, "message", "Amount debited from wallet successfully"));
    }

    @PostMapping("/add")
    public ResponseEntity<?> addMoney(@AuthenticationPrincipal UserDetails userDetails,
                                      @RequestBody Map<String, Object> request) {
        Number rawId = (Number) request.get("userMainId");
        if (rawId == null) {
            rawId = (Number) request.get("customerId");
        }
        Long userMainId = rawId != null
                ? resolveWalletOwnerId(userDetails, rawId.longValue())
                : getCurrentCustomer(userDetails).getUserMain().getId();
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String description = (String) request.get("description");
        walletService.addMoney(userMainId, amount, description);
        return ResponseEntity.ok(Map.of("success", true, "message", "Money added to wallet successfully"));
    }

    @GetMapping("/balance/{userMainId}")
    public ResponseEntity<?> getBalance(@AuthenticationPrincipal UserDetails userDetails,
                                        @PathVariable Long userMainId) {
        BigDecimal balance = walletService.getBalance(resolveWalletOwnerId(userDetails, userMainId));
        return ResponseEntity.ok(Map.of("success", true, "balance", balance));
    }

    @GetMapping("/history/{userMainId}")
    public ResponseEntity<?> getHistory(@AuthenticationPrincipal UserDetails userDetails,
                                        @PathVariable Long userMainId) {
        List<WalletTransaction> history = walletService.getTransactionHistory(resolveWalletOwnerId(userDetails, userMainId));
        return ResponseEntity.ok(Map.of("success", true, "history", history));
    }

    /**
     * Customer apps historically send customerId, while wallet storage is keyed by
     * UserMain. Accept either identifier, but only for the currently authenticated
     * customer so wallet access remains scoped to the caller.
     */
    private Long resolveWalletOwnerId(UserDetails userDetails, Long requestedId) {
        Customer customer = getCurrentCustomer(userDetails);
        Long customerId = customer.getId();
        Long userMainId = customer.getUserMain().getId();

        if (requestedId.equals(customerId) || requestedId.equals(userMainId)) {
            return userMainId;
        }

        throw new IllegalArgumentException("Unauthorized wallet access");
    }

    private Customer getCurrentCustomer(UserDetails userDetails) {
        return customerService.getCustomerByPhone(userDetails.getUsername());
    }
}
