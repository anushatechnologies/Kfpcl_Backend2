package com.project.Anusha.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Returned after a successful POST /api/customer/scratchcards/{id}/scratch
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScratchCardScratchResponse {
    private Long cardId;
    private Integer revealedPoints;
    private Integer revealedRupees;
    private BigDecimal newWalletBalance;
    private String message;
}
