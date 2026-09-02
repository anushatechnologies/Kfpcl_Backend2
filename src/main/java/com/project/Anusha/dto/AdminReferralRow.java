package com.project.Anusha.dto;

import com.project.Anusha.model.Referral;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin panel row view of a referral. Joins referrer + referee identity for the table.
 */
@Data
public class AdminReferralRow {
    private Long id;
    private String status;
    private Integer fraudScore;
    private String fraudReasons;

    private Long referrerUserMainId;
    private String referrerPhone;

    private Long refereeUserMainId;
    private String refereePhone;

    private String referralCode;
    private String signupDeviceId;
    private String signupIp;
    private LocalDateTime createdAt;
    private LocalDateTime qualifiedAt;
    private LocalDateTime rewardedAt;

    public static AdminReferralRow from(Referral r) {
        AdminReferralRow row = new AdminReferralRow();
        row.id = r.getId();
        row.status = r.getStatus().name();
        row.fraudScore = r.getFraudScore();
        row.fraudReasons = r.getFraudReasons();
        if (r.getReferrer() != null) {
            row.referrerUserMainId = r.getReferrer().getId();
            row.referrerPhone = r.getReferrer().getPhoneNumber();
        }
        if (r.getReferee() != null) {
            row.refereeUserMainId = r.getReferee().getId();
            row.refereePhone = r.getReferee().getPhoneNumber();
        }
        row.referralCode = r.getReferralCode();
        row.signupDeviceId = r.getSignupDeviceId();
        row.signupIp = r.getSignupIp();
        row.createdAt = r.getCreatedAt();
        row.qualifiedAt = r.getQualifiedAt();
        row.rewardedAt = r.getRewardedAt();
        return row;
    }
}
