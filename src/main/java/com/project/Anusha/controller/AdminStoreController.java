package com.project.Anusha.controller;

import com.project.Anusha.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for store-wise analytics:
 *   GET /api/admin/stores/dashboard        — all stores with order + income stats
 *   GET /api/admin/stores/{storeId}/orders — orders for a specific store
 *   GET /api/admin/stores/{storeId}/income — income summary for a specific store
 */
@RestController
@RequestMapping("/api/admin/stores")
@RequiredArgsConstructor
public class AdminStoreController {

    private final AdminService adminService;

    /**
     * Returns summary for every store: total orders, delivered orders, total income.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<List<Map<String, Object>>> getStoreDashboard() {
        return ResponseEntity.ok(adminService.getStoreDashboard());
    }

    /**
     * Returns all orders that contain at least one item from the given store.
     */
    @GetMapping("/{storeId}/orders")
    public ResponseEntity<List<Map<String, Object>>> getStoreOrders(@PathVariable Long storeId) {
        try {
            return ResponseEntity.ok(adminService.getOrdersByStore(storeId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Returns income breakdown (today / week / month / total) for a specific store.
     */
    @GetMapping("/{storeId}/income")
    public ResponseEntity<Map<String, Object>> getStoreIncome(@PathVariable Long storeId) {
        try {
            return ResponseEntity.ok(adminService.getStoreIncome(storeId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
