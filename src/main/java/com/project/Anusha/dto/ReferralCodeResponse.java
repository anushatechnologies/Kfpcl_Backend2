package com.project.Anusha.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * GET /api/customer/referral/me
 * The customer's own referral code, share link, and stats.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralCodeResponse {
    private String referralCode;
    private String shareLink;          // deep link the app shares
    private String shareMessage;       // pre-filled WhatsApp message
    private Integer pointsPerRupee;    // 10 (so app shows ₹ correctly)

    private long totalInvited;         // # of QUALIFIED referrals
    private long totalPending;         // # of PENDING referrals
    private long totalRewarded;        // # of REWARDED referrals
    private BigDecimal totalEarnedRupees;  // sum across all referrer scratch cards (in rupees)
}
