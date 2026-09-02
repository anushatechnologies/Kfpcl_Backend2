package com.project.Anusha.service;

import com.google.firebase.auth.FirebaseToken;
import com.project.Anusha.model.UserFcmToken;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.repository.UserFcmTokenRepository;
import com.project.Anusha.repository.UserMainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Central service that manages the {@link UserMain} entity.
 *
 * Key responsibility: given a verified Firebase token, either retrieve the
 * existing UserMain row (for the same uid/phone) or create a new one.
 * This guarantees that a phone number that is registered in both apps
 * still maps to exactly ONE row in users_main.
 */
@Service
@Transactional
public class UserMainService {

    private final UserMainRepository userMainRepository;
    private final UserFcmTokenRepository userFcmTokenRepository;

    public UserMainService(UserMainRepository userMainRepository, UserFcmTokenRepository userFcmTokenRepository) {
        this.userMainRepository = userMainRepository;
        this.userFcmTokenRepository = userFcmTokenRepository;
    }

    /**
     * Look up by Firebase UID first; fall back to phone number.
     * If neither exists, create a new row (without a role yet).
     *
     * The caller must call {@link #addRole(UserMain, String)} afterwards
     * and the entity will be saved again.
     */
    public UserMain findOrCreate(String fid, String phoneNumber) {
        // 1. Try by Firebase UID (most reliable)
        Optional<UserMain> byFid = userMainRepository.findByFid(fid);
        if (byFid.isPresent()) {
            UserMain um = byFid.get();
            // Normalise phone number if it somehow changed
            if (!phoneNumber.equals(um.getPhoneNumber())) {
                um.setPhoneNumber(phoneNumber);
                userMainRepository.save(um);
            }
            return um;
        }

        // 2. Try by phone number (user may have signed in from a new device)
        Optional<UserMain> byPhone = userMainRepository.findByPhoneNumber(phoneNumber);
        if (byPhone.isPresent()) {
            UserMain um = byPhone.get();
            // Sync fid in case it was null or changed
            if (!fid.equals(um.getFid())) {
                um.setFid(fid);
                userMainRepository.save(um);
            }
            return um;
        }

        // 3. Create brand new row
        UserMain newUser = new UserMain(fid, phoneNumber);
        return userMainRepository.save(newUser);
    }

    /**
     * Convenience overload that reads fid + phone from a verified FirebaseToken.
     */
    public UserMain findOrCreate(FirebaseToken token) {
        String fid = token.getUid();
        // Firebase stores phone as "+91..." in the phone_number claim
        String phone = (String) token.getClaims().get("phone_number");
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Firebase token does not contain a phone_number claim");
        }
        return findOrCreate(fid, phone);
    }

    /**
     * Adds a role to the UserMain entity and persists it.
     * Idempotent – calling twice with the same role has no extra effect.
     */
    public UserMain addRole(UserMain userMain, String role) {
        userMain.addRole(role);
        return userMainRepository.save(userMain);
    }

    public Optional<UserMain> findByPhoneNumber(String phoneNumber) {
        return userMainRepository.findByPhoneNumber(phoneNumber);
    }

    public Optional<UserMain> findByFid(String fid) {
        return userMainRepository.findByFid(fid);
    }

    public boolean existsByPhoneNumber(String phoneNumber) {
        return userMainRepository.existsByPhoneNumber(phoneNumber);
    }

    /**
     * Updates/Adds an FCM token for a user by phone number.
     * Supports multiple devices.
     */
    public void updateFcmToken(String phoneNumber, String fcmToken) {
        // Normalise phone number to ensure it has the +91 prefix
        if (!phoneNumber.startsWith("+")) {
            if (phoneNumber.length() == 10) {
                phoneNumber = "+91" + phoneNumber;
            }
        }
        
        Optional<UserMain> userOpt = userMainRepository.findByPhoneNumber(phoneNumber);
        if (userOpt.isPresent()) {
            UserMain user = userOpt.get();
            
            // Check if token already exists for this user
            Optional<UserFcmToken> existingToken = userFcmTokenRepository.findByUserAndFcmToken(user, fcmToken);
            if (existingToken.isEmpty()) {
                UserFcmToken newToken = new UserFcmToken(user, fcmToken);
                userFcmTokenRepository.save(newToken);
                System.out.println("✅ Added new FCM token for user: " + phoneNumber);
            } else {
                // Update timestamp
                UserFcmToken token = existingToken.get();
                userFcmTokenRepository.save(token);
                System.out.println("✅ Refreshed FCM token for user: " + phoneNumber);
            }
        } else {
            System.out.println("⚠️ Could not update FCM token: user not found with phone " + phoneNumber);
        }
    }

    /**
     * Get all active FCM tokens for a phone number
     */
    public List<String> getFcmTokensByPhoneNumber(String phoneNumber) {
        return userMainRepository.findByPhoneNumber(phoneNumber)
                .map(user -> userFcmTokenRepository.findByUser(user).stream()
                        .map(UserFcmToken::getFcmToken)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    /**
     * Get all active FCM tokens for a user entity
     */
    public List<String> getFcmTokensForUser(UserMain user) {
        if (user == null) return Collections.emptyList();
        return userFcmTokenRepository.findByUser(user).stream()
                .map(UserFcmToken::getFcmToken)
                .collect(Collectors.toList());
    }
}
