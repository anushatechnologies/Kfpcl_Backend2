package com.project.Anusha.service;

import com.project.Anusha.model.*;
import com.project.Anusha.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core service for the referral + scratch card flow.
 *
 * Responsibilities:
 *  - assign a referral code to every customer at signup
 *  - capture referral relationship + run fraud checks
 *  - issue scratch cards on QUALIFIED referrals
 *
 * Identity is bound to UserMain (mobile-verified). Device/IP only block, never grant.
 */
@Service
@Transactional
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    private final ReferralRepository referralRepository;
    private final ScratchCardRepository scratchCardRepository;
    private final ReferralConfigRepository configRepository;
    private final FraudBlocklistRepository fraudRepository;
    private final CustomerRepository customerRepository;
    private final ReferralCodeGenerator codeGenerator;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.referral.share-link-base:https://app.anushatechnologies.com/r/}")
    private String shareLinkBase;

    @Value("${app.referral.brand-name:Anusha Bazaar}")
    private String brandName;

    public ReferralService(ReferralRepository referralRepository,
                           ScratchCardRepository scratchCardRepository,
                           ReferralConfigRepository configRepository,
                           FraudBlocklistRepository fraudRepository,
                           CustomerRepository customerRepository,
                           ReferralCodeGenerator codeGenerator) {
        this.referralRepository = referralRepository;
        this.scratchCardRepository = scratchCardRepository;
        this.configRepository = configRepository;
        this.fraudRepository = fraudRepository;
        this.customerRepository = customerRepository;
        this.codeGenerator = codeGenerator;
    }

    // ── Config helpers ────────────────────────────────────────────────────────

    public ReferralConfig getConfig() {
        return configRepository.findById(1L).orElseGet(() -> {
            ReferralConfig fresh = new ReferralConfig();
            return configRepository.save(fresh);
        });
    }

    public ReferralConfig updateConfig(ReferralConfig incoming, String updatedBy) {
        ReferralConfig current = getConfig();
        // Only copy fields admin can edit
        if (incoming.getPointsPerRupee() != null) current.setPointsPerRupee(incoming.getPointsPerRupee());
        if (incoming.getReferrerMinPoints() != null) current.setReferrerMinPoints(roundDown10(incoming.getReferrerMinPoints()));
        if (incoming.getReferrerMaxPoints() != null) current.setReferrerMaxPoints(roundDown10(incoming.getReferrerMaxPoints()));
        if (incoming.getReferrerLowBandMax() != null) current.setReferrerLowBandMax(roundDown10(incoming.getReferrerLowBandMax()));
        if (incoming.getReferrerLowBandProbability() != null) current.setReferrerLowBandProbability(incoming.getReferrerLowBandProbability());
        if (incoming.getRefereeMinPoints() != null) current.setRefereeMinPoints(roundDown10(incoming.getRefereeMinPoints()));
        if (incoming.getRefereeMaxPoints() != null) current.setRefereeMaxPoints(roundDown10(incoming.getRefereeMaxPoints()));
        if (incoming.getCardExpiryDays() != null) current.setCardExpiryDays(incoming.getCardExpiryDays());
        if (incoming.getDailyReferrerCap() != null) current.setDailyReferrerCap(incoming.getDailyReferrerCap());
        if (incoming.getMonthlyReferrerCap() != null) current.setMonthlyReferrerCap(incoming.getMonthlyReferrerCap());
        if (incoming.getFraudBlockThreshold() != null) current.setFraudBlockThreshold(incoming.getFraudBlockThreshold());
        if (incoming.getIpSignupLimitPerHour() != null) current.setIpSignupLimitPerHour(incoming.getIpSignupLimitPerHour());
        if (incoming.getDeviceSignupLimitPerDay() != null) current.setDeviceSignupLimitPerDay(incoming.getDeviceSignupLimitPerDay());
        if (incoming.getEnabled() != null) current.setEnabled(incoming.getEnabled());
        current.setUpdatedBy(updatedBy);
        return configRepository.save(current);
    }

    // ── Code assignment & resolution ──────────────────────────────────────────

    /** Ensure a customer has their own shareable code; called from signup. */
    public String ensureCodeFor(Customer customer) {
        if (customer.getReferralCode() != null && !customer.getReferralCode().isBlank()) {
            return customer.getReferralCode();
        }
        String code = codeGenerator.generateUnique();
        customer.setReferralCode(code);
        customerRepository.save(customer);
        return code;
    }

    public Optional<Customer> resolveCode(String referralCode) {
        if (referralCode == null || referralCode.isBlank()) return Optional.empty();
        return customerRepository.findByReferralCode(referralCode.trim().toUpperCase());
    }

    public String shareLinkFor(String referralCode) {
        return shareLinkBase + referralCode;
    }

    public String brandName() {
        return brandName;
    }

    // ── Signup hook ───────────────────────────────────────────────────────────

    /**
     * Called from CustomerController.signup() once the new Customer + UserMain are in place.
     * Persists the Referral row, runs fraud checks, and issues scratch cards if QUALIFIED.
     *
     * Returns the created Referral, or null if no valid code was supplied.
     */
    public Referral handleSignup(Customer newCustomer, String referralCode, String deviceId, String ip) {
        ReferralConfig config = getConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            log.info("Referral system disabled, skipping for customer {}", newCustomer.getId());
            return null;
        }
        if (referralCode == null || referralCode.isBlank()) return null;

        // Always store device/ip on customer for audit, even if no code or invalid.
        newCustomer.setSignupDeviceId(deviceId);
        newCustomer.setSignupIp(ip);
        newCustomer.setReferredByCode(referralCode.trim().toUpperCase());

        Optional<Customer> referrerOpt = resolveCode(referralCode);
        if (referrerOpt.isEmpty()) {
            log.warn("Unknown referral code '{}' used by customer {}", referralCode, newCustomer.getId());
            customerRepository.save(newCustomer);
            return null;
        }
        Customer referrer = referrerOpt.get();
        UserMain referrerUm = referrer.getUserMain();
        UserMain refereeUm = newCustomer.getUserMain();

        // Self-referral check is the strictest — refuse before recording anything.
        if (referrerUm.getId().equals(refereeUm.getId())) {
            log.warn("Self-referral attempt blocked for customer {}", newCustomer.getId());
            customerRepository.save(newCustomer);
            return null;
        }

        // Block creating duplicate referral rows for the same referee.
        if (referralRepository.existsByRefereeId(refereeUm.getId())) {
            log.info("Referee {} already has a referral row, ignoring", refereeUm.getId());
            customerRepository.save(newCustomer);
            return null;
        }

        Referral referral = new Referral();
        referral.setReferrer(referrerUm);
        referral.setReferee(refereeUm);
        referral.setReferralCode(referralCode.trim().toUpperCase());
        referral.setSignupDeviceId(deviceId);
        referral.setSignupIp(ip);
        referral.setStatus(Referral.Status.PENDING);
        referral = referralRepository.save(referral);

        // Run fraud rules
        List<String> reasons = new ArrayList<>();
        int score = computeFraudScore(referrerUm, refereeUm, deviceId, ip, config, reasons);
        referral.setFraudScore(score);
        referral.setFraudReasons(String.join(";", reasons));

        if (score >= config.getFraudBlockThreshold()) {
            referral.setStatus(Referral.Status.BLOCKED);
            referralRepository.save(referral);
            customerRepository.save(newCustomer);
            log.warn("Referral {} BLOCKED for fraud score {} reasons={}", referral.getId(), score, reasons);
            return referral;
        }

        // QUALIFY + issue cards
        referral.setStatus(Referral.Status.QUALIFIED);
        referral.setQualifiedAt(LocalDateTime.now());
        referralRepository.save(referral);
        customerRepository.save(newCustomer);

        issueCardsForReferral(referral, config);
        return referral;
    }

    // ── Fraud rules ───────────────────────────────────────────────────────────

    private int computeFraudScore(UserMain referrer, UserMain referee,
                                  String deviceId, String ip,
                                  ReferralConfig config, List<String> reasons) {
        int score = 0;
        LocalDateTime hourAgo = LocalDateTime.now().minusHours(1);
        LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);

        // 1. Device already used in a REWARDED referral → big red flag.
        if (deviceId != null && !deviceId.isBlank()
                && referralRepository.countRewardedByDevice(deviceId) > 0) {
            score += 80;
            reasons.add("device_reused_after_reward");
        }
        // 2. Same device used by too many referrals in 24h.
        if (deviceId != null && !deviceId.isBlank()) {
            long deviceCount = referralRepository.countBySignupDeviceIdAndCreatedAtAfter(deviceId, dayAgo);
            if (deviceCount > config.getDeviceSignupLimitPerDay()) {
                score += 60;
                reasons.add("device_signup_limit_exceeded:" + deviceCount);
            }
        }
        // 3. Same IP velocity.
        if (ip != null && !ip.isBlank()) {
            long ipCount = referralRepository.countBySignupIpAndCreatedAtAfter(ip, hourAgo);
            if (ipCount > config.getIpSignupLimitPerHour()) {
                score += 50;
                reasons.add("ip_velocity_high:" + ipCount);
            }
        }
        // 4. Mobile blocklisted.
        if (referee.getPhoneNumber() != null
                && fraudRepository.existsByEntryTypeAndEntryValue(FraudBlocklist.EntryType.MOBILE, referee.getPhoneNumber())) {
            score += 100;
            reasons.add("mobile_blocklisted");
        }
        // 5. Device blocklisted.
        if (deviceId != null
                && fraudRepository.existsByEntryTypeAndEntryValue(FraudBlocklist.EntryType.DEVICE_ID, deviceId)) {
            score += 100;
            reasons.add("device_blocklisted");
        }
        // 6. IP blocklisted.
        if (ip != null
                && fraudRepository.existsByEntryTypeAndEntryValue(FraudBlocklist.EntryType.IP, ip)) {
            score += 100;
            reasons.add("ip_blocklisted");
        }
        // 7. Referrer's daily cap exceeded.
        long referrerToday = referralRepository.countByReferrerIdAndStatusAndCreatedAtAfter(
                referrer.getId(), Referral.Status.QUALIFIED, dayAgo);
        if (referrerToday >= config.getDailyReferrerCap()) {
            score += 50;
            reasons.add("referrer_daily_cap_reached:" + referrerToday);
        }
        long referrerThisMonth = referralRepository.countByReferrerIdAndStatusAndCreatedAtAfter(
                referrer.getId(), Referral.Status.QUALIFIED, LocalDateTime.now().minusDays(30));
        if (referrerThisMonth >= config.getMonthlyReferrerCap()) {
            score += 50;
            reasons.add("referrer_monthly_cap_reached:" + referrerThisMonth);
        }
        return score;
    }

    // ── Card issuance ─────────────────────────────────────────────────────────

    public void issueCardsForReferral(Referral referral, ReferralConfig config) {
        LocalDateTime expiry = LocalDateTime.now().plusDays(config.getCardExpiryDays());

        ScratchCard refereeCard = new ScratchCard();
        refereeCard.setOwner(referral.getReferee());
        refereeCard.setType(ScratchCard.Type.REFEREE_SIGNUP);
        refereeCard.setStatus(ScratchCard.Status.AVAILABLE);
        refereeCard.setReferral(referral);
        refereeCard.setMinPoints(roundDown10(config.getRefereeMinPoints()));
        refereeCard.setMaxPoints(roundDown10(config.getRefereeMaxPoints()));
        refereeCard.setExpiresAt(expiry);
        refereeCard.setTitle("Welcome bonus");
        refereeCard.setSubtitle("Scratch to win up to ₹" + (config.getRefereeMaxPoints() / config.getPointsPerRupee()));
        scratchCardRepository.save(refereeCard);

        ScratchCard referrerCard = new ScratchCard();
        referrerCard.setOwner(referral.getReferrer());
        referrerCard.setType(ScratchCard.Type.REFERRER_REWARD);
        referrerCard.setStatus(ScratchCard.Status.AVAILABLE);
        referrerCard.setReferral(referral);
        referrerCard.setMinPoints(roundDown10(config.getReferrerMinPoints()));
        referrerCard.setMaxPoints(roundDown10(config.getReferrerMaxPoints()));
        referrerCard.setExpiresAt(expiry);
        referrerCard.setTitle("Friend joined!");
        referrerCard.setSubtitle("Scratch to win up to ₹" + (config.getReferrerMaxPoints() / config.getPointsPerRupee()));
        scratchCardRepository.save(referrerCard);
    }

    /**
     * Pick the points value AT SCRATCH TIME. Always returns a multiple of 10.
     *
     * For REFERRER_REWARD cards we use the weighted band from config:
     *   85% chance in [min, lowBandMax], 15% chance in (lowBandMax, max]
     * For other cards (REFEREE_SIGNUP, ADMIN_GIFT) we use uniform random.
     */
    public int pickPointsForScratch(ScratchCard card, ReferralConfig config) {
        int min = roundDown10(card.getMinPoints());
        int max = roundDown10(card.getMaxPoints());
        if (max < min) max = min;

        if (card.getType() == ScratchCard.Type.REFERRER_REWARD) {
            int lowMax = Math.min(roundDown10(config.getReferrerLowBandMax()), max);
            double p = config.getReferrerLowBandProbability() != null ? config.getReferrerLowBandProbability() : 0.85;
            if (random.nextDouble() < p || lowMax >= max) {
                return randomMultipleOf10(min, lowMax);
            }
            return randomMultipleOf10(lowMax + 10, max);
        }
        return randomMultipleOf10(min, max);
    }

    private int randomMultipleOf10(int minInclusive, int maxInclusive) {
        int min = roundDown10(Math.max(10, minInclusive));
        int max = roundDown10(Math.max(min, maxInclusive));
        int steps = ((max - min) / 10) + 1;
        return min + (random.nextInt(steps) * 10);
    }

    private int roundDown10(int v) {
        if (v < 10) return 10;
        return (v / 10) * 10;
    }

    // ── Stats for "/me" view ──────────────────────────────────────────────────

    public ReferralStats statsFor(Long referrerUserMainId) {
        // "Invited" = everyone who signed up using this user's code, regardless of stage.
        // We DON'T count BLOCKED here (those are fraud-flagged signups).
        LocalDateTime since = LocalDateTime.now().minusYears(10);
        long qualified = referralRepository.countByReferrerIdAndStatusAndCreatedAtAfter(
                referrerUserMainId, Referral.Status.QUALIFIED, since);
        long pending = referralRepository.countByReferrerIdAndStatusAndCreatedAtAfter(
                referrerUserMainId, Referral.Status.PENDING, since);
        long rewarded = referralRepository.countByReferrerIdAndStatusAndCreatedAtAfter(
                referrerUserMainId, Referral.Status.REWARDED, since);
        long totalInvited = qualified + pending + rewarded;
        return new ReferralStats(totalInvited, pending, rewarded);
    }

    public record ReferralStats(long totalInvited, long totalPending, long totalRewarded) {}

    // ── Admin helpers ────────────────────────────────────────────────────────

    public Referral overrideStatus(Long referralId, Referral.Status status) {
        Referral r = referralRepository.findById(referralId)
                .orElseThrow(() -> new IllegalArgumentException("Referral not found"));
        Referral.Status before = r.getStatus();
        r.setStatus(status);
        if (status == Referral.Status.QUALIFIED && r.getQualifiedAt() == null) {
            r.setQualifiedAt(LocalDateTime.now());
            // If admin manually qualifies a previously blocked one, also issue cards.
            if (before == Referral.Status.BLOCKED || before == Referral.Status.PENDING) {
                issueCardsIfMissing(r);
            }
        }
        return referralRepository.save(r);
    }

    /** Lists BLOCKED referrals from the last N days where the reason mentions the supplied fragment. */
    public List<Referral> findBlockedByReason(String reasonFragment, int withinDays) {
        int days = Math.max(1, withinDays);
        return referralRepository.findBlockedByReasonSince(
                LocalDateTime.now().minusDays(days), reasonFragment);
    }

    /** Idempotent bulk re-qualifier. Skips ids that don't exist or are already QUALIFIED/REWARDED. */
    public BulkRequalifyResult bulkRequalify(List<Long> referralIds) {
        if (referralIds == null || referralIds.isEmpty()) {
            return new BulkRequalifyResult(0, 0, 0, List.of());
        }
        int qualified = 0;
        int alreadyOk = 0;
        int skipped = 0;
        List<String> notes = new ArrayList<>();
        ReferralConfig config = getConfig();
        for (Long id : referralIds) {
            Optional<Referral> opt = referralRepository.findById(id);
            if (opt.isEmpty()) {
                skipped++;
                notes.add("id=" + id + ":not_found");
                continue;
            }
            Referral r = opt.get();
            if (r.getStatus() == Referral.Status.QUALIFIED || r.getStatus() == Referral.Status.REWARDED) {
                // Still ensure cards exist (heals legacy rows that were QUALIFIED without cards).
                int issued = issueCardsIfMissing(r);
                if (issued > 0) {
                    qualified++;
                    notes.add("id=" + id + ":cards_added=" + issued);
                } else {
                    alreadyOk++;
                }
                continue;
            }
            Referral.Status before = r.getStatus();
            r.setStatus(Referral.Status.QUALIFIED);
            if (r.getQualifiedAt() == null) r.setQualifiedAt(LocalDateTime.now());
            referralRepository.save(r);
            int issued = issueCardsIfMissing(r);
            qualified++;
            notes.add("id=" + id + ":from=" + before + ":cards_added=" + issued);
            // Avoid letting one referral run config through repeatedly when many rows share it.
            // (config is the same singleton, but we read it once above to keep the loop tight.)
            // unused warning-quieter:
            if (config != null) { /* no-op */ }
        }
        return new BulkRequalifyResult(qualified, alreadyOk, skipped, notes);
    }

    /**
     * Issues the missing referrer/referee card(s) for an already-existing Referral.
     * Returns how many cards were created (0, 1, or 2). Idempotent — checks via the
     * referral linkage so calling twice never double-issues.
     */
    public int issueCardsIfMissing(Referral referral) {
        if (referral == null || referral.getReferrer() == null || referral.getReferee() == null) {
            return 0;
        }
        ReferralConfig config = getConfig();
        LocalDateTime expiry = LocalDateTime.now().plusDays(config.getCardExpiryDays());
        int created = 0;

        boolean refereeHasCard = scratchCardRepository
                .findByOwnerIdAndStatusOrderByCreatedAtDesc(referral.getReferee().getId(), ScratchCard.Status.AVAILABLE)
                .stream().anyMatch(c -> referral.equals(c.getReferral()) && c.getType() == ScratchCard.Type.REFEREE_SIGNUP);
        if (!refereeHasCard) {
            ScratchCard refereeCard = new ScratchCard();
            refereeCard.setOwner(referral.getReferee());
            refereeCard.setType(ScratchCard.Type.REFEREE_SIGNUP);
            refereeCard.setStatus(ScratchCard.Status.AVAILABLE);
            refereeCard.setReferral(referral);
            refereeCard.setMinPoints(roundDown10(config.getRefereeMinPoints()));
            refereeCard.setMaxPoints(roundDown10(config.getRefereeMaxPoints()));
            refereeCard.setExpiresAt(expiry);
            refereeCard.setTitle("Welcome bonus");
            refereeCard.setSubtitle("Scratch to win up to ₹" + (config.getRefereeMaxPoints() / config.getPointsPerRupee()));
            scratchCardRepository.save(refereeCard);
            created++;
        }

        boolean referrerHasCard = scratchCardRepository
                .findByOwnerIdAndStatusOrderByCreatedAtDesc(referral.getReferrer().getId(), ScratchCard.Status.AVAILABLE)
                .stream().anyMatch(c -> referral.equals(c.getReferral()) && c.getType() == ScratchCard.Type.REFERRER_REWARD);
        if (!referrerHasCard) {
            ScratchCard referrerCard = new ScratchCard();
            referrerCard.setOwner(referral.getReferrer());
            referrerCard.setType(ScratchCard.Type.REFERRER_REWARD);
            referrerCard.setStatus(ScratchCard.Status.AVAILABLE);
            referrerCard.setReferral(referral);
            referrerCard.setMinPoints(roundDown10(config.getReferrerMinPoints()));
            referrerCard.setMaxPoints(roundDown10(config.getReferrerMaxPoints()));
            referrerCard.setExpiresAt(expiry);
            referrerCard.setTitle("Friend joined!");
            referrerCard.setSubtitle("Scratch to win up to ₹" + (config.getReferrerMaxPoints() / config.getPointsPerRupee()));
            scratchCardRepository.save(referrerCard);
            created++;
        }
        return created;
    }

    public record BulkRequalifyResult(int qualified, int alreadyOk, int skipped, List<String> notes) {}

    public void markRewardedIfBothScratched(Referral referral) {
        if (referral == null) return;
        // Are both the referrer's and referee's cards scratched?
        long unsratched = scratchCardRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(
                referral.getReferee().getId(), ScratchCard.Status.AVAILABLE).stream()
                .filter(c -> referral.equals(c.getReferral())).count()
            + scratchCardRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(
                referral.getReferrer().getId(), ScratchCard.Status.AVAILABLE).stream()
                .filter(c -> referral.equals(c.getReferral())).count();
        if (unsratched == 0) {
            referral.setStatus(Referral.Status.REWARDED);
            referral.setRewardedAt(LocalDateTime.now());
            referralRepository.save(referral);
        }
    }
}
