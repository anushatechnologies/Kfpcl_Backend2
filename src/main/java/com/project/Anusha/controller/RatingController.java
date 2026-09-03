package com.project.Anusha.controller;

import com.project.Anusha.dto.RatingRequest;
import com.project.Anusha.model.ProductRating;
import com.project.Anusha.service.RatingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/products/rating")
@CrossOrigin(origins = "*")
public class RatingController {

    @Autowired
    private RatingService ratingService;

    @PostMapping
    public ResponseEntity<?> addRating(@RequestBody RatingRequest request) {
        try {
            ProductRating rating = ratingService.addRating(request);
            return ResponseEntity.ok(rating);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
