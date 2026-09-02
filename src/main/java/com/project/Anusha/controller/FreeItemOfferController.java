package com.project.Anusha.controller;

import com.project.Anusha.dto.FreeItemOfferResponse;
import com.project.Anusha.service.FreeItemOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/free-item-offers")
@RequiredArgsConstructor
public class FreeItemOfferController {

    private final FreeItemOfferService freeItemOfferService;

    @GetMapping("/active")
    public ResponseEntity<List<FreeItemOfferResponse>> getActiveOffers() {
        return ResponseEntity.ok(freeItemOfferService.getActiveOffers());
    }
}
