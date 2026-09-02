package com.project.Anusha.controller;

import com.project.Anusha.dto.AdminSearchResponse;
import com.project.Anusha.service.AdminSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/search")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminSearchController {

    private final AdminSearchService adminSearchService;

    /**
     * Global admin search (high-impact):
     * Search products, orders, customers in one call.
     *
     * GET /api/admin/search?q=milk&limit=10
     */
    @GetMapping
    public ResponseEntity<AdminSearchResponse> search(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(adminSearchService.search(q, limit));
    }
}

