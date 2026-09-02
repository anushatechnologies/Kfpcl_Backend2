package com.project.Anusha.controller;

import com.project.Anusha.dto.ReferralCodeResponse;
import com.project.Anusha.dto.ReferredMemberRow;
import com.project.Anusha.dto.ScratchCardDto;
import com.project.Anusha.dto.ScratchCardScratchResponse;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Referral;
import com.project.Anusha.model.ReferralConfig;
import com.project.Anusha.model.ScratchCard;
import com.project.Anusha.repository.CustomerRepository;
import com.project.Anusha.repository.ReferralRepository;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.ReferralService;
import com.project.Anusha.service.ScratchCardService;
import com.project.Anusha.service.WalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Customer-facing referral + scratch card API.
 *
 *   GET  /api/customer/referral/me                — my code, share link, stats
 *   GET  /api/customer/referral/validate/{code}   — does this code exist?
 *   GET  /api/customer/scratchcards               — all my cards
 *   POST /api/customer/scratchcards/{id}/scratch  — reveal a card
 */
@RestController
@RequestMapping("/api/customer")
@CrossOrigin(origins = "*")
public class CustomerReferralController {

    private final CustomerService customerService;
    private final ReferralService referralService;
    private final ScratchCardService scratchCardService;
    private final WalletService walletService;
    private final ReferralRepository referralRepository;
    private final CustomerRepository customerRepository;

    public CustomerReferralController(CustomerService customerService,
                                      ReferralService referralService,
                                      ScratchCardService scratchCardService,
                                      WalletService walletService,
                                      ReferralRepository referralRepository,
                                      CustomerRepository customerRepository) {
        this.customerService = customerService;
        this.referralService = referralService;
        this.scratchCardService = scratchCardService;
        this.walletService = walletService;
        this.referralRepository = referralRepository;
        this.customerRepository = customerRepository;
    }

    @GetMapping("/referral/me")
    public ResponseEntity<?> myReferral(Authentication auth) {
        Customer me = customerService.getCustomerByPhone(auth.getName());
        String code = referralService.ensureCodeFor(me);
        ReferralConfig cfg = referralService.getConfig();

        ReferralService.ReferralStats stats = referralService.statsFor(me.getUserMain().getId());

        // Sum revealed points from all REFERRER_REWARD scratched cards
        long earnedPoints = scratchCardService
                .listForOwnerByStatus(me.getUserMain().getId(), ScratchCard.Status.SCRATCHED)
                .stream()
                .filter(c -> c.getType() == ScratchCard.Type.REFERRER_REWARD)
                .mapToInt(c -> c.getRevealedPoints() == null ? 0 : c.getRevealedPoints())
                .sum();
        BigDecimal earnedRupees = BigDecimal.valueOf(earnedPoints)
                .divide(BigDecimal.valueOf(cfg.getPointsPerRupee()), 2, RoundingMode.DOWN);

        String shareLink = referralService.shareLinkFor(code);
        String shareMessage = "Try " + referralService.brandName() + "! Use my code " + code
                + " when you sign up and get up to ₹" + (cfg.getRefereeMaxPoints() / cfg.getPointsPerRupee())
                + " in your wallet. " + shareLink;

        ReferralCodeResponse resp = new ReferralCodeResponse(
                code, shareLink, shareMessage, cfg.getPointsPerRupee(),
                stats.totalInvited(), stats.totalPending(), stats.totalRewarded(),
                earnedRupees);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/referral/validate/{code}")
    public ResponseEntity<?> validate(@PathVariable String code, Authentication auth) {
        Customer me = customerService.getCustomerByPhone(auth.getName());
        return referralService.resolveCode(code)
                .<ResponseEntity<?>>map(referrer -> {
                    boolean isSelf = me.getUserMain().getId().equals(referrer.getUserMain().getId());
                    return ResponseEntity.ok(Map.of(
                            "valid", !isSelf,
                            "selfReferral", isSelf,
                            "referrerName", isSelf ? null : referrer.getUsername()));
                })
                .orElse(ResponseEntity.ok(Map.of("valid", false, "selfReferral", false)));
    }

    /**
     * GET /api/customer/referral/invited
     * History of friends I've referred — for the "Members invited" tab in the app.
     */
    @GetMapping("/referral/invited")
    public ResponseEntity<?> invitedMembers(Authentication auth) {
        Customer me = customerService.getCustomerByPhone(auth.getName());
        ReferralConfig cfg = referralService.getConfig();
        int rate = Math.max(1, cfg.getPointsPerRupee());

        List<Referral> rows = referralRepository.findVisibleByReferrer(me.getUserMain().getId());

        List<ReferredMemberRow> result = rows.stream().map(r -> {
            // Look up referee profile for display name.
            Customer refereeCustomer = customerRepository.findByUserMainId(r.getReferee().getId()).orElse(null);
            String name = refereeCustomer != null ? refereeCustomer.getUsername() : "Friend";
            String maskedPhone = mask(r.getReferee().getPhoneNumber());

            // Earned rupees from THIS specific friend = points on the referrer card linked to this referral.
            int earnedPoints = scratchCardService
                    .listForOwner(me.getUserMain().getId()).stream()
                    .filter(c -> c.getReferral() != null
                            && r.getId().equals(c.getReferral().getId())
                            && c.getStatus() == ScratchCard.Status.SCRATCHED
                            && c.getRevealedPoints() != null)
                    .mapToInt(ScratchCard::getRevealedPoints)
                    .sum();

            return new ReferredMemberRow(
                    r.getId(),
                    name,
                    maskedPhone,
                    r.getStatus().name(),
                    r.getCreatedAt(),
                    earnedPoints / rate);
        }).toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "members", result,
                "totalCount", result.size()));
    }

    /** "+919948598350" → "+91 ******50" */
    private String mask(String phone) {
        if (phone == null) return "";
        if (phone.length() < 6) return phone;
        int last = phone.length() - 2;
        StringBuilder sb = new StringBuilder();
        sb.append(phone, 0, 3); // country code + 1
        sb.append(' ');
        for (int i = 3; i < last; i++) sb.append('*');
        sb.append(phone, last, phone.length());
        return sb.toString();
    }

    @GetMapping("/scratchcards")
    public ResponseEntity<?> myCards(Authentication auth) {
        Customer me = customerService.getCustomerByPhone(auth.getName());
        ReferralConfig cfg = referralService.getConfig();
        List<ScratchCardDto> cards = scratchCardService.listForOwner(me.getUserMain().getId())
                .stream()
                .map(c -> ScratchCardDto.from(c, cfg.getPointsPerRupee()))
                .toList();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "pointsPerRupee", cfg.getPointsPerRupee(),
                "cards", cards));
    }

    @PostMapping("/scratchcards/{id}/scratch")
    public ResponseEntity<?> scratch(@PathVariable Long id, Authentication auth) {
        Customer me = customerService.getCustomerByPhone(auth.getName());
        try {
            ScratchCardScratchResponse resp = scratchCardService.scratch(id, me.getUserMain().getId());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
