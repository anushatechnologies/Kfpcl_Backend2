package com.project.Anusha.controller;

import com.project.Anusha.dto.ContactSellerRequest;
import com.project.Anusha.model.ContactLead;
import com.project.Anusha.service.LeadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/buyer")
@CrossOrigin(origins = "*")
public class BuyerLeadController {

    @Autowired
    private LeadService leadService;

    @PostMapping("/contact-seller")
    public ResponseEntity<?> contactSeller(@RequestBody ContactSellerRequest request) {
        try {
            ContactLead lead = leadService.recordLead(request);
            return ResponseEntity.ok(lead);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
