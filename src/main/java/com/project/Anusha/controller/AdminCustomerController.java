package com.project.Anusha.controller;

import com.project.Anusha.dto.CustomerProfileResponse;
import com.project.Anusha.model.Customer;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {

    private final CustomerService customerService;
    private final WalletService walletService;

    public AdminCustomerController(CustomerService customerService, WalletService walletService) {
        this.customerService = customerService;
        this.walletService = walletService;
    }

    /**
     * GET /api/admin/customers
     * Optional query params:
     *   search  – filter by name or phone (partial match)
     *   page    – 0-based page number (default 0)
     *   size    – page size (default 20)
     *   active  – true/false filter (omit for all)
     *
     * Example: GET /api/admin/customers?search=abhi&page=0&size=10
     */
    @GetMapping
    public ResponseEntity<?> getAllCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Customer> customerPage = customerService.searchCustomers(search, active, pageable);

        List<CustomerProfileResponse> data = customerPage.getContent()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("customers", data);
        result.put("totalElements", customerPage.getTotalElements());
        result.put("totalPages", customerPage.getTotalPages());
        result.put("currentPage", customerPage.getNumber());

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/admin/customers/{id}
     * Get a single customer by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCustomerById(@PathVariable Long id) {
        Customer customer = customerService.getCustomerById(id);
        return ResponseEntity.ok(toResponse(customer));
    }

    /**
     * PUT /api/admin/customers/{id}/status
     * Activate or deactivate a customer.
     * Body: { "active": true }  or  { "active": false }
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {

        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().body("Field 'active' is required");
        }

        Customer customer = customerService.setCustomerActive(id, active);
        return ResponseEntity.ok(toResponse(customer));
    }

    /**
     * DELETE /api/admin/customers/{id}
     * Permanently delete a customer.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.ok(Map.of("message", "Customer deleted successfully"));
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private CustomerProfileResponse toResponse(Customer c) {
        CustomerProfileResponse r = new CustomerProfileResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setPhoneNumber(c.getPhoneNumber());
        r.setEmail(c.getEmail());
        r.setIsActive(c.getIsActive());
        r.setCreatedAt(c.getCreatedAt());
        r.setUpdatedAt(c.getUpdatedAt());
        if (c.getUserMain() != null) {
            try {
                r.setWalletBalance(walletService.getBalance(c.getUserMain().getId()));
            } catch (Exception ignored) {
                r.setWalletBalance(java.math.BigDecimal.ZERO);
            }
        } else {
            r.setWalletBalance(java.math.BigDecimal.ZERO);
        }
        return r;
    }
}

