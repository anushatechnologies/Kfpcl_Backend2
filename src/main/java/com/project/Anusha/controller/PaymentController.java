package com.project.Anusha.controller;

import com.project.Anusha.dto.PaymentRefundRequest;
import com.project.Anusha.model.Customer;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;
    private final CustomerService customerService;

    @PostMapping("/refund/request")
    public ResponseEntity<?> requestRefund(
            @RequestHeader(value = "X-Customer-Id", required = false) Long customerIdHeader,
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @RequestBody PaymentRefundRequest request) {
        Long customerId = customerIdHeader != null ? customerIdHeader : customerIdParam;
        Customer customer = customerId != null ? customerService.getCustomerById(customerId) : null;
        return ResponseEntity.ok(paymentService.processRefund(
                request.getOrderId(),
                customer,
                request.getAmount(),
                request.getReason()));
    }

    @GetMapping("/refund-status/{orderId}")
    public ResponseEntity<?> getRefundStatus(
            @RequestHeader(value = "X-Customer-Id", required = false) Long customerIdHeader,
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @PathVariable Long orderId) {
        Long customerId = customerIdHeader != null ? customerIdHeader : customerIdParam;
        Customer customer = customerId != null ? customerService.getCustomerById(customerId) : null;
        return ResponseEntity.ok(paymentService.getRefundStatus(orderId, customer));
    }
}
