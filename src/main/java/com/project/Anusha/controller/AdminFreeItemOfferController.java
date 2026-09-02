package com.project.Anusha.controller;

import com.project.Anusha.dto.FreeItemOfferRequest;
import com.project.Anusha.dto.FreeItemOfferResponse;
import com.project.Anusha.service.FreeItemOfferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/free-item-offers")
@CrossOrigin(origins = "*")
public class AdminFreeItemOfferController {

    private final FreeItemOfferService freeItemOfferService;

    public AdminFreeItemOfferController(FreeItemOfferService freeItemOfferService) {
        this.freeItemOfferService = freeItemOfferService;
    }

    @GetMapping
    public ResponseEntity<List<FreeItemOfferResponse>> getAllOffers() {
        return ResponseEntity.ok(freeItemOfferService.getAllOffers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOffer(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(freeItemOfferService.getOffer(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> createOffer(@RequestBody FreeItemOfferRequest request) {
        try {
            return ResponseEntity.ok(freeItemOfferService.createOffer(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateOffer(@PathVariable Long id, @RequestBody FreeItemOfferRequest request) {
        try {
            return ResponseEntity.ok(freeItemOfferService.updateOffer(id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOffer(@PathVariable Long id) {
        try {
            freeItemOfferService.deleteOffer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Free item offer deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
