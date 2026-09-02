package com.project.Anusha.controller;

import com.project.Anusha.dto.AdminReferralRow;
import com.project.Anusha.dto.AdminScratchCardRow;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.FraudBlocklist;
import com.project.Anusha.model.Referral;
import com.project.Anusha.model.ReferralConfig;
import com.project.Anusha.model.ScratchCard;
import com.project.Anusha.repository.CustomerRepository;
import com.project.Anusha.repository.FraudBlocklistRepository;
import com.project.Anusha.repository.ReferralRepository;
import com.project.Anusha.repository.ScratchCardRepository;
import com.project.Anusha.service.ReferralService;
import com.project.Anusha.service.ScratchCardService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for the referral / scratch card subsystem.
 *
 *   GET    /api/admin/referrals                — paged list with filters
 *   PATCH  /api/admin/referrals/{id}/status    — manual status override
 *
 *   GET    /api/admin/scratchcards             — paged list with filters
 *   POST   /api/admin/scratchcards/gift        — admin-issued ADMIN_GIFT card
 *   PATCH  /api/admin/scratchcards/{id}/revoke — revoke an unscratched card
 *
 *   GET    /api/admin/referral-config          — read current config
 *   PUT    /api/admin/referral-config          — update config
 *
 *   GET    /api/admin/fraud/blocklist          — list blocklist entries
 *   POST   /api/admin/fraud/blocklist          — add entry
 *   DELETE /api/admin/fraud/blocklist/{id}     — remove entry
 *   GET    /api/admin/fraud/queue              — pending blocked referrals
 *
 *   GET    /api/admin/referrals/stats          — dashboard counters
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminReferralController {

    private final ReferralRepository referralRepository;
    private final ScratchCardRepository scratchCardRepository;
    private final FraudBlocklistRepository fraudRepository;
    private final CustomerRepository customerRepository;
    private final ReferralService referralService;
    private final ScratchCardService scratchCardService;

    public AdminReferralController(ReferralRepository referralRepository,
                                   ScratchCardRepository scratchCardRepository,
                                   FraudBlocklistRepository fraudRepository,
                                   CustomerRepository customerRepository,
                                   ReferralService referralService,
                                   ScratchCardService scratchCardService) {
        this.referralRepository = referralRepository;
        this.scratchCardRepository = scratchCardRepository;
        this.fraudRepository = fraudRepository;
        this.customerRepository = customerRepository;
        this.referralService = referralService;
        this.scratchCardService = scratchCardService;
    }

    // ── Referrals ─────────────────────────────────────────────────────────────

    @GetMapping("/referrals")
    public ResponseEntity<?> list(@RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Referral.Status filter = null;
        if (status != null && !status.isBlank()) {
            try { filter = Referral.Status.valueOf(status.toUpperCase()); } catch (Exception ignored) {}
        }
        Page<Referral> result = filter != null
                ? referralRepository.findByStatus(filter, pageable)
                : referralRepository.findAll(pageable);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "rows", result.getContent().stream().map(AdminReferralRow::from).toList()));
    }

    @PatchMapping("/referrals/{id}/status")
    public ResponseEntity<?> overrideStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null) return ResponseEntity.badRequest().body(Map.of("error", "status required"));
        try {
            Referral.Status target = Referral.Status.valueOf(status.toUpperCase());
            Referral updated = referralService.overrideStatus(id, target);
            return ResponseEntity.ok(AdminReferralRow.from(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lists BLOCKED referrals from the last N days whose fraud_reasons mention the given fragment.
     * Common values for `reason`:
     *   - "referrer_daily_cap_reached"   (the 5-per-day cap)
     *   - "referrer_monthly_cap_reached" (the monthly cap)
     *   - "ip_velocity_high"
     *   - "device_signup_limit_exceeded"
     * Pass `reason=cap` to match both daily and monthly cap blocks.
     */
    @GetMapping("/referrals/blocked-by-reason")
    public ResponseEntity<?> blockedByReason(@RequestParam(defaultValue = "cap") String reason,
                                             @RequestParam(defaultValue = "2") int days) {
        List<Referral> rows = referralService.findBlockedByReason(reason, days);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "totalElements", rows.size(),
                "rows", rows.stream().map(AdminReferralRow::from).toList()));
    }

    /**
     * Bulk re-qualify previously BLOCKED referrals and issue any missing scratch cards.
     * Body: { "ids": [1, 2, 3] }
     * Already-QUALIFIED rows that are missing cards will also have their cards backfilled.
     */
    @PostMapping("/referrals/bulk-requalify")
    public ResponseEntity<?> bulkRequalify(@RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        if (!(raw instanceof List<?> list)) {
            return ResponseEntity.badRequest().body(Map.of("error", "ids array required"));
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number n) ids.add(n.longValue());
            else if (item instanceof String s) {
                try { ids.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "no valid ids provided"));
        }
        ReferralService.BulkRequalifyResult result = referralService.bulkRequalify(ids);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "qualified", result.qualified(),
                "alreadyOk", result.alreadyOk(),
                "skipped", result.skipped(),
                "notes", result.notes()));
    }

    // ── Scratch cards ─────────────────────────────────────────────────────────

    @GetMapping("/scratchcards")
    public ResponseEntity<?> listCards(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String type,
                                       @RequestParam(required = false) String phone,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        ScratchCard.Status s = null;
        ScratchCard.Type t = null;
        try {
            if (status != null && !status.isBlank()) s = ScratchCard.Status.valueOf(status.toUpperCase());
            if (type != null && !type.isBlank()) t = ScratchCard.Type.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid status or type"));
        }
        String phoneFilter = (phone != null && !phone.isBlank()) ? phone.trim() : null;
        Page<ScratchCard> result = scratchCardRepository.adminSearch(s, t, phoneFilter, pageable);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "rows", result.getContent().stream().map(AdminScratchCardRow::from).toList()));
    }

    /** Counters tailored to the Scratch Cards admin page header. */
    @GetMapping("/scratchcards/stats")
    public ResponseEntity<?> scratchCardStats() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        long pointsAll = scratchCardRepository.totalRevealedPoints();
        long points30d = scratchCardRepository.totalRevealedPointsAfter(since);
        ReferralConfig cfg = referralService.getConfig();
        int rate = Math.max(1, cfg.getPointsPerRupee());
        return ResponseEntity.ok(Map.of(
                "available", scratchCardRepository.countByStatus(ScratchCard.Status.AVAILABLE),
                "scratched", scratchCardRepository.countByStatus(ScratchCard.Status.SCRATCHED),
                "expired", scratchCardRepository.countByStatus(ScratchCard.Status.EXPIRED),
                "revoked", scratchCardRepository.countByStatus(ScratchCard.Status.REVOKED),
                "pointsAll", pointsAll,
                "points30d", points30d,
                "rupeesAll", pointsAll / rate,
                "rupees30d", points30d / rate,
                "pointsPerRupee", rate));
    }

    @PostMapping("/scratchcards/gift")
    public ResponseEntity<?> giftCard(@RequestBody Map<String, Object> body) {
        Long ownerUserMainId = ((Number) body.get("userMainId")).longValue();
        int min = ((Number) body.getOrDefault("minPoints", 10)).intValue();
        int max = ((Number) body.getOrDefault("maxPoints", 50)).intValue();
        int days = ((Number) body.getOrDefault("expiryDays", 30)).intValue();
        String title = (String) body.getOrDefault("title", "Surprise from us");
        String subtitle = (String) body.get("subtitle");
        try {
            ScratchCard card = scratchCardService.issueAdminGift(ownerUserMainId, min, max, days, title, subtitle);
            return ResponseEntity.ok(AdminScratchCardRow.from(card));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/scratchcards/{id}/revoke")
    public ResponseEntity<?> revoke(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        try {
            ScratchCard card = scratchCardService.revoke(id, reason);
            return ResponseEntity.ok(AdminScratchCardRow.from(card));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lists organic signups (no referral code used) from the last N days who
     * haven't yet received an ADMIN_GIFT scratch card. Default window = 2 days
     * which covers "yesterday + today".
     */
    @GetMapping("/scratchcards/unreferred-new-users")
    public ResponseEntity<?> listUnreferredNewUsers(@RequestParam(defaultValue = "2") int days) {
        int window = Math.max(1, Math.min(30, days));
        List<Customer> rows = customerRepository
                .findUnreferredOrganicSignups(LocalDateTime.now().minusDays(window));
        List<Map<String, Object>> json = rows.stream().map(c -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("customerId", c.getId());
            row.put("userMainId", c.getUserMain() != null ? c.getUserMain().getId() : null);
            row.put("name", c.getName());
            row.put("phoneNumber", c.getUserMain() != null ? c.getUserMain().getPhoneNumber() : null);
            row.put("email", c.getEmail());
            row.put("createdAt", c.getCreatedAt());
            return row;
        }).toList();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "windowDays", window,
                "totalElements", json.size(),
                "rows", json));
    }

    /**
     * Bulk-gift ADMIN_GIFT scratch cards to a list of UserMain ids. Idempotent —
     * any user that already has at least one ADMIN_GIFT card is skipped.
     *
     * Body:
     * {
     *   "userMainIds": [12, 13, 14],
     *   "minPoints":   10,
     *   "maxPoints":   100,
     *   "expiryDays":  30,
     *   "title":       "Welcome to Anusha Bazaar",
     *   "subtitle":    "Scratch to win up to ₹10"
     * }
     */
    @PostMapping("/scratchcards/bulk-gift")
    public ResponseEntity<?> bulkGift(@RequestBody Map<String, Object> body) {
        Object raw = body.get("userMainIds");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userMainIds array required"));
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number n) ids.add(n.longValue());
            else if (item instanceof String s) {
                try { ids.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "no valid userMainIds provided"));
        }

        int min = ((Number) body.getOrDefault("minPoints", 10)).intValue();
        int max = ((Number) body.getOrDefault("maxPoints", 50)).intValue();
        int days = ((Number) body.getOrDefault("expiryDays", 30)).intValue();
        String title = (String) body.getOrDefault("title", "Welcome bonus");
        String subtitle = (String) body.get("subtitle");

        ScratchCardService.BulkGiftResult result =
                scratchCardService.bulkIssueAdminGift(ids, min, max, days, title, subtitle);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "issued", result.issued(),
                "skipped", result.skipped(),
                "requested", ids.size()));
    }

    // ── Config ────────────────────────────────────────────────────────────────

    @GetMapping("/referral-config")
    public ResponseEntity<?> getConfig() {
        return ResponseEntity.ok(referralService.getConfig());
    }

    @PutMapping("/referral-config")
    public ResponseEntity<?> updateConfig(@RequestBody ReferralConfig incoming, Authentication auth) {
        String who = auth != null ? auth.getName() : "system";
        return ResponseEntity.ok(referralService.updateConfig(incoming, who));
    }

    // ── Fraud queue & blocklist ───────────────────────────────────────────────

    @GetMapping("/fraud/queue")
    public ResponseEntity<?> fraudQueue() {
        List<Referral> blocked = referralRepository.findTop20ByStatusOrderByCreatedAtDesc(Referral.Status.BLOCKED);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "rows", blocked.stream().map(AdminReferralRow::from).toList()));
    }

    @GetMapping("/fraud/blocklist")
    public ResponseEntity<?> listBlocklist(@RequestParam(required = false) String type,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<FraudBlocklist> result;
        if (type != null && !type.isBlank()) {
            try {
                result = fraudRepository.findByEntryType(FraudBlocklist.EntryType.valueOf(type.toUpperCase()), p);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid type"));
            }
        } else {
            result = fraudRepository.findAll(p);
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "totalElements", result.getTotalElements(),
                "rows", result.getContent()));
    }

    @PostMapping("/fraud/blocklist")
    public ResponseEntity<?> addBlocklist(@RequestBody Map<String, String> body, Authentication auth) {
        String type = body.get("type");
        String value = body.get("value");
        String reason = body.get("reason");
        if (type == null || value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "type and value are required"));
        }
        try {
            FraudBlocklist.EntryType t = FraudBlocklist.EntryType.valueOf(type.toUpperCase());
            if (fraudRepository.existsByEntryTypeAndEntryValue(t, value)) {
                return ResponseEntity.badRequest().body(Map.of("error", "already in blocklist"));
            }
            FraudBlocklist entry = new FraudBlocklist();
            entry.setEntryType(t);
            entry.setEntryValue(value);
            entry.setReason(reason);
            entry.setCreatedBy(auth != null ? auth.getName() : "system");
            return ResponseEntity.ok(fraudRepository.save(entry));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid type"));
        }
    }

    @DeleteMapping("/fraud/blocklist/{id}")
    public ResponseEntity<?> removeBlocklist(@PathVariable Long id) {
        if (!fraudRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        fraudRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Stats for admin dashboard ────────────────────────────────────────────

    @GetMapping("/referrals/stats")
    public ResponseEntity<?> stats() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return ResponseEntity.ok(Map.of(
                "totalReferrals", referralRepository.count(),
                "qualifiedAll", referralRepository.findByStatus(Referral.Status.QUALIFIED, PageRequest.of(0, 1)).getTotalElements(),
                "blockedAll", referralRepository.findByStatus(Referral.Status.BLOCKED, PageRequest.of(0, 1)).getTotalElements(),
                "rewardedAll", referralRepository.findByStatus(Referral.Status.REWARDED, PageRequest.of(0, 1)).getTotalElements(),
                "scratchCardsAvailable", scratchCardRepository.countByStatus(ScratchCard.Status.AVAILABLE),
                "scratchCardsScratched", scratchCardRepository.countByStatus(ScratchCard.Status.SCRATCHED),
                "totalPointsRevealed", scratchCardRepository.totalRevealedPoints(),
                "pointsRevealed30d", scratchCardRepository.totalRevealedPointsAfter(since)));
    }
}
