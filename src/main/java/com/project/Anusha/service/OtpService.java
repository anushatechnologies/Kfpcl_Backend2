package com.project.Anusha.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private final SecureRandom secureRandom = new SecureRandom();
    
    // In a real application, you might want to use Redis or a database for OTP storage
    // For now, we'll use an in-memory map (note: this will be lost on server restart)
    private final Map<String, OtpData> otpStore = new HashMap<>();

    /**
     * Generate a 6-digit OTP
     */
    public String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }

    /**
     * Store OTP with expiry time
     */
    public void storeOtp(String key, String otp) {
        OtpData otpData = new OtpData(otp, LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpStore.put(key, otpData);
    }

    /**
     * Verify OTP
     */
    public boolean verifyOtp(String key, String providedOtp) {
        OtpData otpData = otpStore.get(key);
        if (otpData == null) {
            return false;
        }

        // Check if OTP has expired
        if (LocalDateTime.now().isAfter(otpData.expiryTime)) {
            otpStore.remove(key);
            return false;
        }

        // Check if OTP matches
        if (otpData.otp.equals(providedOtp)) {
            otpStore.remove(key); // Remove OTP after successful verification
            return true;
        }

        return false;
    }

    /**
     * Generate and store OTP for a key
     */
    public String generateAndStoreOtp(String key) {
        String otp = generateOtp();
        storeOtp(key, otp);
        return otp;
    }

    /**
     * Check if OTP exists and is valid
     */
    public boolean isOtpValid(String key) {
        OtpData otpData = otpStore.get(key);
        if (otpData == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(otpData.expiryTime)) {
            otpStore.remove(key);
            return false;
        }

        return true;
    }

    /**
     * Remove OTP for a key
     */
    public void removeOtp(String key) {
        otpStore.remove(key);
    }

    /**
     * Get remaining time in seconds for OTP
     */
    public long getRemainingTimeInSeconds(String key) {
        OtpData otpData = otpStore.get(key);
        if (otpData == null) {
            return 0;
        }

        long remainingSeconds = java.time.Duration.between(LocalDateTime.now(), otpData.expiryTime).getSeconds();
        return Math.max(0, remainingSeconds);
    }

    /**
     * Inner class to store OTP data with expiry
     */
    private static class OtpData {
        private final String otp;
        private final LocalDateTime expiryTime;

        public OtpData(String otp, LocalDateTime expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }

        public String getOtp() {
            return otp;
        }

        public LocalDateTime getExpiryTime() {
            return expiryTime;
        }
    }
}
