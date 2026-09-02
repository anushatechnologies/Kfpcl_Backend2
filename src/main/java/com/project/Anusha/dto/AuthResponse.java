package com.project.Anusha.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * Common authentication response returned after successful login / signup.
 * Used by both CustomerApp and DeliveryApp auth endpoints.
 */
@Data
public class AuthResponse {
    private String jwtToken;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private Long customerId;       // customer or delivery person DB id
    private String phoneNumber;
    private String name;           // username for customer, full name for delivery person
    private String email;
    private BigDecimal walletBalance;
    private String roles;          // e.g. "CUSTOMER" or "DELIVERY_PERSON" or "CUSTOMER,DELIVERY_PERSON"
    private String referralCode;   // customer's own code (null for delivery person)
}
