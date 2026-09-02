package com.project.Anusha.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.project.Anusha.dto.AdminAccessTimeRequest;
import com.project.Anusha.model.User;
import com.project.Anusha.repository.UserRepository;
import com.project.Anusha.util.AdminPermissions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern ADMIN_ACCESS_CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final long MAX_ADMIN_ACCESS_DAYS = 3650;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void updateLastLogin(User user) {
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public User getValidatedAdminByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        validateAdminCanAccessPanel(user);

        return user;
    }

    public User completeFirebaseLogin(String email) {
        User user = getValidatedAdminByEmail(email);
        return finalizeSuccessfulLogin(user);
    }

    public User finalizeSuccessfulLogin(User user) {
        user.setMustChangePassword(false);
        user.setLastLoginAt(LocalDateTime.now());
        user.setAdminAccessChallengeToken(null);
        user.setAdminAccessChallengeExpiry(null);
        return userRepository.save(user);
    }

    public boolean requiresAdminAccessCode(User user) {
        return "ROLE_ADMIN".equals(user.getRole());
    }

    public String initiateAdminAccessChallenge(User user) {
        if (!requiresAdminAccessCode(user)) {
            throw new RuntimeException("Only admin accounts require an access code");
        }

        if (!user.hasAdminAccessCode()) {
            throw new RuntimeException("Admin access code is not configured. Please contact your super admin.");
        }

        user.setAdminAccessChallengeToken(UUID.randomUUID().toString());
        user.setAdminAccessChallengeExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);
        return user.getAdminAccessChallengeToken();
    }

    public User verifyAdminAccessCode(String challengeToken, String code) {
        validateAdminAccessCode(code);

        User user = userRepository.findByAdminAccessChallengeToken(challengeToken)
                .orElseThrow(() -> new RuntimeException("Admin access verification expired. Please log in again."));

        if (!user.isEnabled()) {
            throw new RuntimeException("This admin account is disabled");
        }
        if (user.isAdminAccessExpired()) {
            throw new RuntimeException("This admin access time has expired. Please contact your super admin.");
        }
        if (!requiresAdminAccessCode(user)) {
            throw new RuntimeException("This account does not require admin access verification");
        }
        if (user.getAdminAccessChallengeExpiry() == null ||
                user.getAdminAccessChallengeExpiry().isBefore(LocalDateTime.now())) {
            user.setAdminAccessChallengeToken(null);
            user.setAdminAccessChallengeExpiry(null);
            userRepository.save(user);
            throw new RuntimeException("Admin access verification expired. Please log in again.");
        }
        if (!user.hasAdminAccessCode()) {
            throw new RuntimeException("Admin access code is not configured. Please contact your super admin.");
        }
        if (!passwordEncoder.matches(code, user.getAdminAccessCodeHash())) {
            throw new RuntimeException("Invalid admin access code");
        }

        return finalizeSuccessfulLogin(user);
    }

    public User setAdminAccessCode(Long adminId, String code, Long superAdminId) {
        validateAdminAccessCode(code);

        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        if ("ROLE_SUPER_ADMIN".equals(user.getRole())) {
            throw new RuntimeException("Super admin does not use this access code");
        }

        user.setAdminAccessCodeHash(passwordEncoder.encode(code));
        user.setAdminAccessCodeUpdatedAt(LocalDateTime.now());
        user.setAdminAccessChallengeToken(null);
        user.setAdminAccessChallengeExpiry(null);
        return userRepository.save(user);
    }

    public User updateAdminAccessTime(Long adminId, AdminAccessTimeRequest request, Long superAdminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        if ("ROLE_SUPER_ADMIN".equals(user.getRole())) {
            throw new RuntimeException("Super admin access time cannot be limited from here");
        }

        Duration duration = buildAdminAccessDuration(request);
        user.setAdminAccessExpiresAt(LocalDateTime.now().plusSeconds(duration.getSeconds()));
        user.setAdminAccessChallengeToken(null);
        user.setAdminAccessChallengeExpiry(null);
        return userRepository.save(user);
    }

    public User updateAdminPermissions(Long adminId, List<String> permissions, Long superAdminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        if ("ROLE_SUPER_ADMIN".equals(user.getRole())) {
            throw new RuntimeException("Super admin always has all permissions");
        }

        user.setPermissionKeys(AdminPermissions.serialize(permissions));
        user.setAdminAccessChallengeToken(null);
        user.setAdminAccessChallengeExpiry(null);
        return userRepository.save(user);
    }

    public List<String> getEffectivePermissions(User user) {
        if (user == null) {
            return List.of();
        }
        if ("ROLE_SUPER_ADMIN".equals(user.getRole())) {
            return AdminPermissions.all();
        }
        return AdminPermissions.parse(user.getPermissionKeys());
    }

    public Long getAdminAccessRemainingSeconds(User user) {
        if (user == null || user.getAdminAccessExpiresAt() == null) {
            return null;
        }
        return Math.max(0, Duration.between(LocalDateTime.now(), user.getAdminAccessExpiresAt()).getSeconds());
    }

    public void validateAdminCanAccessPanel(User user) {
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (!user.isEnabled()) {
            throw new RuntimeException("This admin account is disabled");
        }
        if (user.isAdminAccessExpired()) {
            throw new RuntimeException("This admin access time has expired. Please contact your super admin.");
        }
    }

    private void validateAdminAccessCode(String code) {
        if (code == null || !ADMIN_ACCESS_CODE_PATTERN.matcher(code.trim()).matches()) {
            throw new RuntimeException("Admin access code must be exactly 6 digits");
        }
    }

    private Duration buildAdminAccessDuration(AdminAccessTimeRequest request) {
        if (request == null) {
            throw new RuntimeException("Access time is required");
        }

        long days = readDurationPart(request.getDays(), "Days", MAX_ADMIN_ACCESS_DAYS);
        long hours = readDurationPart(request.getHours(), "Hours", 23);
        long minutes = readDurationPart(request.getMinutes(), "Minutes", 59);
        long seconds = readDurationPart(request.getSeconds(), "Seconds", 59);

        Duration duration = Duration.ofDays(days)
                .plusHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds);

        if (duration.isZero() || duration.isNegative()) {
            throw new RuntimeException("Access time must be greater than 0 seconds");
        }

        return duration;
    }

    private long readDurationPart(Long value, String label, long max) {
        long safeValue = value == null ? 0 : value;
        if (safeValue < 0 || safeValue > max) {
            throw new RuntimeException(label + " must be between 0 and " + max);
        }
        return safeValue;
    }

    public void preparePasswordReset(String email) {
        User user = getValidatedAdminByEmail(email);

        emailService.ensureFirebaseUserReadyForPasswordReset(user.getEmail(), user.getName());
        emailService.sendAdminPasswordResetEmail(user.getEmail(), user.getName());
    }

    public User syncPasswordFromFirebase(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        validateAdminCanAccessPanel(user);
        if (newPassword == null || newPassword.length() < 8) {
            throw new RuntimeException("New password must be at least 8 characters");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtp(null);
        user.setOtpExpiry(null);
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        user.setAdminAccessChallengeToken(null);
        user.setAdminAccessChallengeExpiry(null);
        user.setMustChangePassword(false);
        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public void changePasswordForAdmin(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }
        if (newPassword.length() < 8) {
            throw new RuntimeException("New password must be at least 8 characters");
        }

        emailService.syncFirebasePassword(user.getEmail(), user.getName(), newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtp(null);
        user.setOtpExpiry(null);
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        user.setAdminAccessChallengeToken(null);
        user.setAdminAccessChallengeExpiry(null);
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    public User createAdminUser(String email, String name, Long superAdminId) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        String placeholderPassword = emailService.generateSystemPassword();

        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPassword(passwordEncoder.encode(placeholderPassword));
        user.setRole("ROLE_ADMIN");
        user.setEnabled(true);
        user.setMustChangePassword(true);
        user.setCreatedById(superAdminId);
        user.setCreatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        try {
            emailService.ensureFirebaseUserExists(email, name, placeholderPassword);
            emailService.sendAdminSetupEmail(email, name);
        } catch (RuntimeException e) {
            throw e;
        }

        return saved;
    }

    public void resetAdminPassword(Long adminId, Long superAdminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        if ("ROLE_SUPER_ADMIN".equals(user.getRole())) {
            throw new RuntimeException("Cannot reset super admin password from here");
        }

        user.setMustChangePassword(true);
        user.setOtp(null);
        user.setOtpExpiry(null);
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        user.setAdminAccessChallengeToken(null);
        user.setAdminAccessChallengeExpiry(null);
        userRepository.save(user);

        emailService.ensureFirebaseUserReadyForPasswordReset(user.getEmail(), user.getName());
        emailService.sendAdminPasswordResetEmail(user.getEmail(), user.getName());
    }

    public User toggleAdminStatus(Long adminId, Long superAdminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        if ("ROLE_SUPER_ADMIN".equals(user.getRole())) {
            throw new RuntimeException("Cannot disable super admin");
        }

        boolean previousEnabled = user.isEnabled();
        user.setEnabled(!user.isEnabled());
        User saved = userRepository.save(user);

        try {
            emailService.updateFirebaseUserDisabled(saved.getEmail(), !saved.isEnabled());
            return saved;
        } catch (RuntimeException e) {
            saved.setEnabled(previousEnabled);
            userRepository.save(saved);
            throw e;
        }
    }

    public List<User> getAllAdminUsers() {
        return userRepository.findByRoleIn(Arrays.asList("ROLE_ADMIN", "ROLE_SUPER_ADMIN"));
    }

    public boolean validatePasswordResetToken(String token) {
        User user = userRepository.findByResetToken(token).orElse(null);
        if (user == null) return false;
        return user.getResetTokenExpiry() != null && user.getResetTokenExpiry().isAfter(LocalDateTime.now());
    }

    public void changePassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }
}
