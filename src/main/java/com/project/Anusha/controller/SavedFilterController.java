package com.project.Anusha.controller;

import com.project.Anusha.model.SavedFilter;
import com.project.Anusha.service.SavedFilterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/saved-filters")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SavedFilterController {

    private final SavedFilterService savedFilterService;

    @GetMapping
    public ResponseEntity<List<SavedFilter>> list(@RequestParam(required = false) String resource) {
        return ResponseEntity.ok(savedFilterService.list(resource));
    }

    @PostMapping
    public ResponseEntity<SavedFilter> create(@RequestBody Map<String, Object> payload, Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(savedFilterService.create(payload, actor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavedFilter> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(savedFilterService.update(id, payload));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        savedFilterService.delete(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}

