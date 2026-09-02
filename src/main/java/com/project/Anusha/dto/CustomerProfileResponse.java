// ─── File 1: UpdateProfileRequest.java ───────────────────────────────────────


// ─── File 2: CustomerProfileResponse.java ────────────────────────────────────
package com.project.Anusha.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CustomerProfileResponse {
    private Long id;
    private String name;
    private String phoneNumber;
    private String email;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal walletBalance;
}

