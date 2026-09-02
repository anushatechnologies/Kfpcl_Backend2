package com.project.Anusha.controller;

import com.project.Anusha.dto.UpdateProfileRequest;
import com.project.Anusha.dto.CustomerProfileResponse;
import com.project.Anusha.model.Customer;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/customer")
public class CustomerProfileController {

    private final CustomerService customerService;
    private final WalletService walletService;

    public CustomerProfileController(CustomerService customerService, WalletService walletService) {
        this.customerService = customerService;
        this.walletService = walletService;
    }

    /**
     * GET /api/customer/profile
     * Returns the logged-in customer's profile.
     * JWT must be present (phone number is the principal).
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        String phoneNumber = authentication.getName(); // phone from JWT
        Customer customer = customerService.getCustomerByPhone(phoneNumber);

        CustomerProfileResponse response = new CustomerProfileResponse();
        response.setId(customer.getId());
        response.setName(customer.getName());
        response.setPhoneNumber(customer.getPhoneNumber());
        response.setEmail(customer.getEmail());
        response.setIsActive(customer.getIsActive());
        response.setCreatedAt(customer.getCreatedAt());
        response.setUpdatedAt(customer.getUpdatedAt());
        if (customer.getUserMain() != null) {
            response.setWalletBalance(walletService.getBalance(customer.getUserMain().getId()));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/customer/profile
     * Update logged-in customer's name and/or email.
     * Body: { "name": "New Name", "email": "optional@email.com" }
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request) {

        String phoneNumber = authentication.getName(); // phone from JWT
        Customer customer = customerService.getCustomerByPhone(phoneNumber);

        if (request.getName() != null && !request.getName().isBlank()) {
            customer.setName(request.getName().trim());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            customer.setEmail(request.getEmail().trim());
        }

        Customer updated = customerService.updateCustomer(customer);

        CustomerProfileResponse response = new CustomerProfileResponse();
        response.setId(updated.getId());
        response.setName(updated.getName());
        response.setPhoneNumber(updated.getPhoneNumber());
        response.setEmail(updated.getEmail());
        response.setIsActive(updated.getIsActive());
        response.setCreatedAt(updated.getCreatedAt());
        response.setUpdatedAt(updated.getUpdatedAt());
        if (updated.getUserMain() != null) {
            response.setWalletBalance(walletService.getBalance(updated.getUserMain().getId()));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/customer/profile
     *
     * Self-service account deletion (required by Google Play data-safety policy).
     * Soft-deletes the customer: marks them inactive, anonymizes name/email,
     * and revokes every active refresh token across CustomerApp + Website.
     * Order history is preserved for accounting purposes.
     */
    @DeleteMapping("/profile")
    public ResponseEntity<?> deleteOwnAccount(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }
        try {
            customerService.deleteOwnAccount(authentication.getName());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Account deleted successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
