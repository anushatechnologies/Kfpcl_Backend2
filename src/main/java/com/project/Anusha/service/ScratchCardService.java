package com.project.Anusha.service;

import com.project.Anusha.dto.ScratchCardScratchResponse;
import com.project.Anusha.model.Referral;
import com.project.Anusha.model.ReferralConfig;
import com.project.Anusha.model.ScratchCard;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.repository.ScratchCardRepository;
import com.project.Anusha.repository.UserMainRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read scratch cards and reveal them atomically.
 *
 * Reveal flow (one DB transaction):
 *   1. SELECT ... FOR UPDATE (pessimistic lock on the card row)
 *   2. Verify status == AVAILABLE and not expired
 *   3. Generate revealedPoints (multiples of 10) — NEVER stored before reveal
 *   4. Credit wallet with the rupee value (points / pointsPerRupee)
 *   5. Update card status → SCRATCHED, set scratchedAt
 *   6. If parent referral is now fully scratched → mark REWARDED
 */
@Service
public class ScratchCardService {

    private static final Logger log = LoggerFactory.getLogger(ScratchCardService.class);

    private final ScratchCardRepository scratchCardRepository;
    private final ReferralService referralService;
    private final WalletService walletService;
    private final UserMainRepository userMainRepository;

    public ScratchCardService(ScratchCardRepository scratchCardRepository,
                              ReferralService referralService,
                              WalletService walletService,
                              UserMainRepository userMainRepository) {
        this.scratchCardRepository = scratchCardRepository;
        this.referralService = referralService;
        this.walletService = walletService;
        this.userMainRepository = userMainRepository;
    }

    @Transactional(readOnly = true)
    public List<ScratchCard> listForOwner(Long ownerUserMainId) {
        return scratchCardRepository.findByOwnerIdOrderByCreatedAtDesc(ownerUserMainId);
    }

    @Transactional(readOnly = true)
    public List<ScratchCard> listForOwnerByStatus(Long ownerUserMainId, ScratchCard.Status status) {
        return scratchCardRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(ownerUserMainId, status);
    }

    @Transactional
    public ScratchCardScratchResponse scratch(Long cardId, Long ownerUserMainId) {
        ScratchCard card = scratchCardRepository.lockForScratch(cardId, ownerUserMainId)
                .orElseThrow(() -> new IllegalArgumentException("Scratch card not found"));

        if (card.getStatus() != ScratchCard.Status.AVAILABLE) {
            throw new IllegalStateException("Card not available (status=" + card.getStatus() + ")");
        }
        if (card.getExpiresAt() != null && card.getExpiresAt().isBefore(LocalDateTime.now())) {
            card.setStatus(ScratchCard.Status.EXPIRED);
            scratchCardRepository.save(card);
            throw new IllegalStateException("Card expired");
        }

        ReferralConfig config = referralService.getConfig();
        int points = referralService.pickPointsForScratch(card, config);

        card.setRevealedPoints(points);
        card.setStatus(ScratchCard.Status.SCRATCHED);
        card.setScratchedAt(LocalDateTime.now());
        scratchCardRepository.save(card);

        // Credit wallet (points / pointsPerRupee = rupees)
        int rupees = points / Math.max(1, config.getPointsPerRupee());
        BigDecimal rupeeAmount = BigDecimal.valueOf(rupees);
        UserMain owner = card.getOwner();
        String desc = "Scratch card reward (" + points + " pts) — " + card.getType().name();
        walletService.addMoney(owner.getId(), rupeeAmount, desc);
        BigDecimal newBalance = walletService.getBalance(owner.getId());

        // If both cards from the same referral are scratched, mark referral REWARDED.
        Referral referral = card.getReferral();
        if (referral != null) {
            referralService.markRewardedIfBothScratched(referral);
        }

        log.info("Scratch reveal: card={} owner={} points={} ({} rupees) newBalance={}",
                card.getId(), owner.getId(), points, rupees, newBalance);

        return new ScratchCardScratchResponse(
                card.getId(), points, rupees, newBalance,
                "You won ₹" + rupees + "! Added to your wallet.");
    }

    /** Admin-side helpers. */
    @Transactional
    public ScratchCard revoke(Long cardId, String reason) {
        ScratchCard card = scratchCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found"));
        if (card.getStatus() == ScratchCard.Status.SCRATCHED) {
            throw new IllegalStateException("Cannot revoke a scratched card");
        }
        card.setStatus(ScratchCard.Status.REVOKED);
        return scratchCardRepository.save(card);
    }

    @Transactional
    public ScratchCard issueAdminGift(Long ownerUserMainId, int minPoints, int maxPoints,
                                      int expiryDays, String title, String subtitle) {
        // Validation handled here so admin endpoints stay thin.
        if (minPoints < 10) minPoints = 10;
        if (maxPoints < minPoints) maxPoints = minPoints;
        // Round to multiples of 10
        minPoints = (minPoints / 10) * 10;
        maxPoints = (maxPoints / 10) * 10;

        UserMain owner = userMainRepository.findById(ownerUserMainId)
                .orElseThrow(() -> new IllegalArgumentException("UserMain not found: " + ownerUserMainId));

        ScratchCard card = new ScratchCard();
        card.setOwner(owner);
        card.setType(ScratchCard.Type.ADMIN_GIFT);
        card.setStatus(ScratchCard.Status.AVAILABLE);
        card.setMinPoints(minPoints);
        card.setMaxPoints(maxPoints);
        card.setExpiresAt(LocalDateTime.now().plusDays(expiryDays));
        card.setTitle(title != null ? title : "Surprise gift");
        card.setSubtitle(subtitle);
        return scratchCardRepository.save(card);
    }

    /**
     * Bulk-issue ADMIN_GIFT cards to the given UserMain ids. Skips any owner that
     * already has at least one ADMIN_GIFT card (idempotent — safe to re-run).
     * Returns a small summary the admin UI can show as a toast.
     */
    @Transactional
    public BulkGiftResult bulkIssueAdminGift(List<Long> userMainIds, int minPoints, int maxPoints,
                                             int expiryDays, String title, String subtitle) {
        int issued = 0;
        int skipped = 0;
        for (Long id : userMainIds) {
            if (id == null) { skipped++; continue; }
            boolean already = scratchCardRepository.existsByOwnerIdAndType(id, ScratchCard.Type.ADMIN_GIFT);
            if (already) { skipped++; continue; }
            try {
                issueAdminGift(id, minPoints, maxPoints, expiryDays, title, subtitle);
                issued++;
            } catch (IllegalArgumentException e) {
                skipped++;
            }
        }
        return new BulkGiftResult(issued, skipped);
    }

    public record BulkGiftResult(int issued, int skipped) {}
}
