package com.project.Anusha.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One entry in the customer-facing "Friends I invited" history list.
 * Phone is masked to protect privacy of the friend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferredMemberRow {
    private Long referralId;
    private String name;            // friend's username
    private String maskedPhone;     // e.g. "+91 ******84"
    private String status;          // PENDING | QUALIFIED | REWARDED
    private LocalDateTime joinedAt; // when they signed up
    private Integer earnedRupees;   // rupees the *referrer* (current user) earned from this friend
}
