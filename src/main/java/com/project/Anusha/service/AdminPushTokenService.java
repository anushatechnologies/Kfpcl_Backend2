package com.project.Anusha.service;

import com.project.Anusha.dto.SavePushTokenRequest;
import com.project.Anusha.model.AdminPushToken;
import com.project.Anusha.model.User;
import com.project.Anusha.repository.AdminPushTokenRepository;
import com.project.Anusha.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminPushTokenService {

    private final AdminPushTokenRepository repo;
    private final UserRepository userRepository;

    public AdminPushTokenService(AdminPushTokenRepository repo, UserRepository userRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
    }

    /**
     * Idempotent upsert keyed by the Expo push token.
     *
     * If the token row exists, the admin link is overwritten — that's the right
     * behaviour when admin A logs out and admin B logs into the same device.
     */
    @Transactional
    public void upsert(Authentication auth, SavePushTokenRequest req) {
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("Not authenticated");
        }
        User admin = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Admin not found: " + auth.getName()));

        AdminPushToken token = repo.findByExpoPushToken(req.expoPushToken())
                .orElseGet(AdminPushToken::new);

        token.setAdmin(admin);
        token.setExpoPushToken(req.expoPushToken());
        token.setPlatform(req.platform() == null ? "android" : req.platform().toLowerCase());
        token.setAppType(req.appType() == null ? "ADMIN_APP" : req.appType());
        token.setActive(true);
        repo.save(token);
    }

    /** Called from the app on logout / Delete Account. Safe to call with an unknown token. */
    @Transactional
    public void deactivate(String expoPushToken) {
        if (expoPushToken == null || expoPushToken.isBlank()) return;
        repo.findByExpoPushToken(expoPushToken).ifPresent(t -> {
            t.setActive(false);
            repo.save(t);
        });
    }

    /** Called by ExpoPushService when Expo replies `DeviceNotRegistered` for a token. */
    @Transactional
    public void markInactiveOnExpoError(String expoPushToken) {
        deactivate(expoPushToken);
    }

    @Transactional(readOnly = true)
    public List<AdminPushToken> findAllActive() {
        return repo.findAllActiveAdminApp();
    }
}
