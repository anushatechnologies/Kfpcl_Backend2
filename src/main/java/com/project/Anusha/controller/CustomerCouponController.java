package com.project.Anusha.controller;

import com.project.Anusha.model.Customer;
import com.project.Anusha.service.CouponService;
import com.project.Anusha.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
@CrossOrigin(origins = "*")
public class CustomerCouponController {

    private final CouponService couponService;
    private final CustomerService customerService;

    public CustomerCouponController(CouponService couponService, CustomerService customerService) {
        this.couponService = couponService;
        this.customerService = customerService;
    }

    @GetMapping("/active")
    public ResponseEntity<List<com.project.Anusha.model.Coupon>> getActiveCoupons() {
        return ResponseEntity.ok(couponService.getActiveCoupons());
    }

    /**
     * Apply a coupon code to calculate discount
     * GET /api/coupons/apply?code=WELCOME10&customerId=1&cartValue=500
     */
    @GetMapping("/apply")
    public ResponseEntity<?> applyCoupon(@RequestParam String code,
                                         @RequestParam Long customerId,
                                         @RequestParam BigDecimal cartValue) {
        try {
            Customer customer = customerService.getCustomerById(customerId);
            if (customer == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Customer not found"));
            }

            BigDecimal discount = couponService.validateAndCalculateDiscount(code, customer, cartValue);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "code", code,
                    "discount", discount,
                    "finalValue", cartValue.subtract(discount)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to apply coupon: " + e.getMessage()));
        }
    }
}
