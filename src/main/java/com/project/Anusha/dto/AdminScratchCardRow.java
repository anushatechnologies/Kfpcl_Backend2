package com.project.Anusha.dto;

import com.project.Anusha.model.ScratchCard;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminScratchCardRow {
    private Long id;
    private Long ownerUserMainId;
    private String ownerPhone;
    private String type;
    private String status;
    private Integer minPoints;
    private Integer maxPoints;
    private Integer revealedPoints;
    private Long sourceReferralId;
    private LocalDateTime createdAt;
    private LocalDateTime scratchedAt;
    private LocalDateTime expiresAt;

    public static AdminScratchCardRow from(ScratchCard s) {
        AdminScratchCardRow row = new AdminScratchCardRow();
        row.id = s.getId();
        if (s.getOwner() != null) {
            row.ownerUserMainId = s.getOwner().getId();
            row.ownerPhone = s.getOwner().getPhoneNumber();
        }
        row.type = s.getType().name();
        row.status = s.getStatus().name();
        row.minPoints = s.getMinPoints();
        row.maxPoints = s.getMaxPoints();
        row.revealedPoints = s.getRevealedPoints();
        row.sourceReferralId = s.getReferral() != null ? s.getReferral().getId() : null;
        row.createdAt = s.getCreatedAt();
        row.scratchedAt = s.getScratchedAt();
        row.expiresAt = s.getExpiresAt();
        return row;
    }
}
