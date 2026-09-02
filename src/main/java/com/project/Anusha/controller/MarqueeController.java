package com.project.Anusha.controller;

import com.project.Anusha.model.MarqueeAnnouncement;
import com.project.Anusha.repository.MarqueeAnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MarqueeController {

    private final MarqueeAnnouncementRepository repo;

    /** GET /api/marquee — public, returns active items for the customer app */
    @GetMapping("/api/marquee")
    public ResponseEntity<List<MarqueeAnnouncement>> getActive() {
        return ResponseEntity.ok(repo.findByActiveTrueOrderByDisplayOrderAsc());
    }

    /** GET /api/admin/marquee — all items for admin panel */
    @GetMapping("/api/admin/marquee")
    public ResponseEntity<List<MarqueeAnnouncement>> getAll() {
        return ResponseEntity.ok(repo.findAllByOrderByDisplayOrderAsc());
    }

    /** POST /api/admin/marquee — create */
    @PostMapping("/api/admin/marquee")
    public ResponseEntity<MarqueeAnnouncement> create(@RequestBody MarqueeAnnouncement body) {
        body.setId(null);
        return ResponseEntity.ok(repo.save(body));
    }

    /** PUT /api/admin/marquee/{id} — update */
    @PutMapping("/api/admin/marquee/{id}")
    public ResponseEntity<MarqueeAnnouncement> update(
            @PathVariable Long id,
            @RequestBody MarqueeAnnouncement body) {
        return repo.findById(id).map(existing -> {
            existing.setText(body.getText());
            existing.setActive(body.isActive());
            existing.setDisplayOrder(body.getDisplayOrder());
            return ResponseEntity.ok(repo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /api/admin/marquee/{id} — delete */
    @DeleteMapping("/api/admin/marquee/{id}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
