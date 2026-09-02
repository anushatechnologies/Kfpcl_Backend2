package com.project.Anusha.controller;

import com.project.Anusha.dto.DeliveryRatingRequest;
import com.project.Anusha.model.Customer;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.DeliveryRatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Customer-facing rating endpoints.
 *
 * POST /api/orders/{orderNumber}/rate       — submit 1-5 star rating + optional feedback
 * GET  /api/orders/{orderNumber}/rating     — check if order can be rated / already rated
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class CustomerRatingController {

    private final DeliveryRatingService deliveryRatingService;
    private final CustomerService customerService;

    /**
     * Submit delivery person rating after order is delivered.
     *
     * Body: { "rating": 4, "feedback": "Fast delivery!" }
     * feedback is optional — customer can skip it.
     */
    @PostMapping("/{orderNumber}/rate")
    public ResponseEntity<?> submitRating(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String orderNumber,
            @Valid @RequestBody DeliveryRatingRequest request) {

        Customer customer = customerService.getCustomerByPhone(userDetails.getUsername());
        Map<String, Object> result = deliveryRatingService.submitRating(
                orderNumber,
                customer,
                request.getRating(),
                request.getFeedback()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Check rating status for an order.
     * Frontend uses this to decide whether to show the rating prompt.
     *
     * Response:
     * {
     *   "orderNumber": "ORD123",
     *   "delivered": true,
     *   "alreadyRated": false,
     *   "rating": 0,
     *   "feedback": "",
     *   "canRate": true       ← true only if delivered && not already rated
     * }
     */
    @GetMapping("/{orderNumber}/rating")
    public ResponseEntity<?> getRatingStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String orderNumber) {

        Customer customer = customerService.getCustomerByPhone(userDetails.getUsername());
        Map<String, Object> result = deliveryRatingService.getRatingStatus(orderNumber, customer);
        return ResponseEntity.ok(result);
    }
}
