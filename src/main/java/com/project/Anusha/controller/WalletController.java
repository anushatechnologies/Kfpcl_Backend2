package com.project.Anusha.controller;

import com.project.Anusha.model.Customer;
import com.project.Anusha.model.WalletTransaction;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    @PostMapping({"/debit", "/spend", "/deduct"})
    public ResponseEntity<?> debitMoney(
            @RequestHeader(value = "X-Customer-Id", required = false) Long customerIdHeader,
            @RequestHeader(value = "X-Phone-Number", required = false) String phoneHeader,
            @RequestBody Map<String, Object> request) {

        Long userMainId = resolveUserMainId(customerIdHeader, phoneHeader, request, null);
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String description = (String) request.getOrDefault("description", "Wallet payment");
        walletService.deductMoney(userMainId, amount, description);
        return ResponseEntity.ok(Map.of("success", true, "message", "Amount debited from wallet successfully"));
    }

    @PostMapping("/add")
    public ResponseEntity<?> addMoney(
            @RequestHeader(value = "X-Customer-Id", required = false) Long customerIdHeader,
            @RequestHeader(value = "X-Phone-Number", required = false) String phoneHeader,
            @RequestBody Map<String, Object> request) {

        Long userMainId = resolveUserMainId(customerIdHeader, phoneHeader, request, null);
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String description = (String) request.getOrDefault("description", "Wallet top-up");
        walletService.addMoney(userMainId, amount, description);
        return ResponseEntity.ok(Map.of("success", true, "message", "Money added to wallet successfully"));
    }

    @GetMapping("/balance/{userMainId}")
    public ResponseEntity<?> getBalance(
            @RequestHeader(value = "X-Customer-Id", required = false) Long customerIdHeader,
            @RequestHeader(value = "X-Phone-Number", required = false) String phoneHeader,
            @PathVariable Long userMainId) {

        Long resolvedId = resolveUserMainId(customerIdHeader, phoneHeader, null, userMainId);
        BigDecimal balance = walletService.getBalance(resolvedId);
        return ResponseEntity.ok(Map.of("success", true, "balance", balance));
    }

    @GetMapping("/history/{userMainId}")
    public ResponseEntity<?> getHistory(
            @RequestHeader(value = "X-Customer-Id", required = false) Long customerIdHeader,
            @RequestHeader(value = "X-Phone-Number", required = false) String phoneHeader,
            @PathVariable Long userMainId) {

        Long resolvedId = resolveUserMainId(customerIdHeader, phoneHeader, null, userMainId);
        List<WalletTransaction> history = walletService.getTransactionHistory(resolvedId);
        return ResponseEntity.ok(Map.of("success", true, "history", history));
    }

    private Long resolveUserMainId(Long customerIdHeader, String phoneHeader, Map<String, Object> request, Long pathId) {
        if (pathId != null) {
            Customer customer = customerService.getCustomerById(pathId);
            if (customer != null && customer.getUserMain() != null) {
                return customer.getUserMain().getId();
            }
            return pathId;
        }

        if (customerIdHeader != null) {
            Customer customer = customerService.getCustomerById(customerIdHeader);
            if (customer != null && customer.getUserMain() != null) {
                return customer.getUserMain().getId();
            }
        }

        if (phoneHeader != null && !phoneHeader.isBlank()) {
            Customer customer = customerService.getCustomerByPhone(phoneHeader);
            if (customer != null && customer.getUserMain() != null) {
                return customer.getUserMain().getId();
            }
        }

        if (request != null) {
            Number rawId = (Number) request.get("userMainId");
            if (rawId == null) rawId = (Number) request.get("customerId");
            if (rawId == null) rawId = (Number) request.get("userId");
            if (rawId != null) {
                Customer customer = customerService.getCustomerById(rawId.longValue());
                if (customer != null && customer.getUserMain() != null) {
                    return customer.getUserMain().getId();
                }
                return rawId.longValue();
            }
        }

        throw new IllegalArgumentException("Customer identification missing. Provide customerId or X-Customer-Id header.");
    }
}
