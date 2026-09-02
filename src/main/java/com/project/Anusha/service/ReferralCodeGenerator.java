package com.project.Anusha.service;

import com.project.Anusha.repository.CustomerRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates short, human-shareable referral codes (6 chars, A-Z + 2-9, ambiguous chars stripped).
 * Persists nothing — just produces a code that the caller writes onto Customer.
 */
@Component
public class ReferralCodeGenerator {

    // Excludes 0/O/1/I/L/U to avoid confusion when shared verbally.
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789".toCharArray();
    private static final int LENGTH = 6;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final CustomerRepository customerRepository;

    public ReferralCodeGenerator(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public String generateUnique() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String code = randomCode();
            if (!customerRepository.existsByReferralCode(code)) {
                return code;
            }
        }
        // Extremely unlikely with 29^6 ≈ 5.94 × 10^8 codespace; fall back to longer code
        return randomCode() + randomCode().substring(0, 2);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
