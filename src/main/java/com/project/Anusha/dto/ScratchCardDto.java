package com.project.Anusha.dto;

import com.project.Anusha.model.ScratchCard;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Customer-facing scratch card view.
 *
 * IMPORTANT: revealedPoints is null for AVAILABLE cards — points are generated
 * at scratch time, never returned before scratch.
 */
@Data
public class ScratchCardDto {
    private Long id;
    private String type;           // REFERRER_REWARD | REFEREE_SIGNUP | ADMIN_GIFT
    private String status;         // AVAILABLE | SCRATCHED | EXPIRED | REVOKED
    private Integer minPoints;     // shown as the displayed range on card front
    private Integer maxPoints;
    private Integer revealedPoints;// null until scratched
    private Integer revealedRupees;// helper: revealedPoints / pointsPerRupee
    private String title;
    private String subtitle;
    private LocalDateTime expiresAt;
    private LocalDateTime scratchedAt;
    private LocalDateTime createdAt;

    public static ScratchCardDto from(ScratchCard card, int pointsPerRupee) {
        ScratchCardDto dto = new ScratchCardDto();
        dto.id = card.getId();
        dto.type = card.getType().name();
        dto.status = card.getStatus().name();
        dto.minPoints = card.getMinPoints();
        dto.maxPoints = card.getMaxPoints();
        dto.revealedPoints = card.getRevealedPoints();
        dto.revealedRupees = card.getRevealedPoints() != null
                ? card.getRevealedPoints() / pointsPerRupee : null;
        dto.title = card.getTitle();
        dto.subtitle = card.getSubtitle();
        dto.expiresAt = card.getExpiresAt();
        dto.scratchedAt = card.getScratchedAt();
        dto.createdAt = card.getCreatedAt();
        return dto;
    }
}
