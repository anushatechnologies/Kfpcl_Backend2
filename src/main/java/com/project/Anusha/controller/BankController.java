package com.project.Anusha.controller;

import com.project.Anusha.model.Bank;
import com.project.Anusha.repository.BankRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Public bank search API — used by delivery partner app during signup.
 * GET /api/banks/search?q=state  → returns matching bank names (max 10)
 * GET /api/banks               → returns all banks
 */
@RestController
@RequestMapping("/api/banks")
@CrossOrigin(origins = "*")
public class BankController {

    @Autowired
    private BankRepository bankRepository;

    @GetMapping
    public ResponseEntity<?> getAllBanks() {
        List<Bank> banks = bankRepository.findAll();
        return ResponseEntity.ok(Map.of("success", true, "banks", banks));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchBanks(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) {
            List<Bank> all = bankRepository.findAll();
            List<Bank> top = all.stream().limit(10).toList();
            return ResponseEntity.ok(Map.of("success", true, "banks", top));
        }
        List<Bank> results = bankRepository.searchByName(q).stream().limit(10).toList();
        return ResponseEntity.ok(Map.of("success", true, "banks", results));
    }
}
