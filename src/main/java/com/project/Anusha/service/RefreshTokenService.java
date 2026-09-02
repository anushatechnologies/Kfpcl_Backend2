package com.project.Anusha.service;

import com.project.Anusha.model.RefreshToken;
import com.project.Anusha.model.User;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.repository.RefreshTokenRepository;
import com.project.Anusha.repository.UserRepository;
import com.project.Anusha.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@Transactional
public class RefreshTokenService {

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiration-days}")
    private long refreshTokenDays;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               JwtUtils jwtUtils) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
    }

    public TokenPair issueForUserMain(UserMain userMain, RefreshToken.ClientType clientType) {
        return issueNewSession(
                RefreshToken.PrincipalType.USER_MAIN,
                userMain.getId(),
                userMain.getPhoneNumber(),
                clientType
        );
    }

    public TokenPair issueForAdmin(User user) {
        return issueForAdmin(user, RefreshToken.ClientType.ADMIN_PANEL);
    }

    public TokenPair issueForAdmin(User user, RefreshToken.ClientType clientType) {
        if (clientType != RefreshToken.ClientType.ADMIN_PANEL
                && clientType != RefreshToken.ClientType.ADMIN_APP) {
            throw new IllegalArgumentException("Admin sessions can only be issued for ADMIN_PANEL or ADMIN_APP");
        }
        return issueNewSession(
                RefreshToken.PrincipalType.ADMIN,
                user.getId(),
                user.getEmail(),
                clientType
        );
    }

    public TokenPair rotate(String refreshToken, RefreshToken.ClientType expectedClientType) {
        RefreshToken existing = getTokenOrThrow(refreshToken);

        if (existing.getClientType() != expectedClientType) {
            throw new IllegalArgumentException("Refresh token does not belong to this client");
        }

        LocalDateTime now = LocalDateTime.now();

        if (existing.isRevoked()) {
            revokeAllActiveTokens(existing.getPrincipalType(), existing.getPrincipalId(), existing.getClientType(), now);
            throw new IllegalArgumentException("Refresh token reuse detected. Please login again.");
        }

        if (existing.isExpiredAt(now)) {
            existing.setRevoked(true);
            existing.setRevokedAt(now);
            refreshTokenRepository.save(existing);
            throw new IllegalArgumentException("Refresh token expired. Please login again.");
        }

        if (existing.getPrincipalType() == RefreshToken.PrincipalType.ADMIN) {
            assertAdminRefreshAllowed(existing, now);
        }

        existing.setRevoked(true);
        existing.setRevokedAt(now);

        RefreshToken replacement = buildRefreshToken(
                existing.getPrincipalType(),
                existing.getPrincipalId(),
                existing.getSubject(),
                existing.getClientType(),
                now
        );
        existing.setReplacedByToken(replacement.getToken());

        refreshTokenRepository.save(existing);
        refreshTokenRepository.save(replacement);

        return new TokenPair(
                jwtUtils.generateTokenFromSubject(existing.getSubject()),
                replacement.getToken(),
                jwtUtils.getExpirationSeconds()
        );
    }

    public void revoke(String refreshToken, RefreshToken.ClientType expectedClientType) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByToken(refreshToken.trim()).ifPresent(token -> {
            if (token.getClientType() != expectedClientType || token.isRevoked()) {
                return;
            }
            token.setRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    private TokenPair issueNewSession(
            RefreshToken.PrincipalType principalType,
            Long principalId,
            String subject,
            RefreshToken.ClientType clientType
    ) {
        LocalDateTime now = LocalDateTime.now();
        if (requiresSingleActiveSession(clientType)) {
            revokeAllActiveTokens(principalType, principalId, clientType, now);
        }

        RefreshToken refreshToken = buildRefreshToken(principalType, principalId, subject, clientType, now);
        refreshTokenRepository.save(refreshToken);

        return new TokenPair(
                jwtUtils.generateTokenFromSubject(subject),
                refreshToken.getToken(),
                jwtUtils.getExpirationSeconds()
        );
    }

    private boolean requiresSingleActiveSession(RefreshToken.ClientType clientType) {
        return clientType != RefreshToken.ClientType.CUSTOMER_APP;
    }

    private void revokeAllActiveTokens(
            RefreshToken.PrincipalType principalType,
            Long principalId,
            RefreshToken.ClientType clientType,
            LocalDateTime revokedAt
    ) {
        List<RefreshToken> activeTokens = refreshTokenRepository
                .findByPrincipalTypeAndPrincipalIdAndClientTypeAndRevokedFalse(principalType, principalId, clientType);

        if (activeTokens.isEmpty()) {
            return;
        }

        for (RefreshToken token : activeTokens) {
            token.setRevoked(true);
            token.setRevokedAt(revokedAt);
        }

        refreshTokenRepository.saveAll(activeTokens);
    }

    private RefreshToken buildRefreshToken(
            RefreshToken.PrincipalType principalType,
            Long principalId,
            String subject,
            RefreshToken.ClientType clientType,
            LocalDateTime now
    ) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(generateSecureTokenValue());
        refreshToken.setPrincipalType(principalType);
        refreshToken.setPrincipalId(principalId);
        refreshToken.setSubject(subject);
        refreshToken.setClientType(clientType);
        refreshToken.setExpiresAt(now.plusDays(refreshTokenDays));
        refreshToken.setRevoked(false);
        refreshToken.setRevokedAt(null);
        refreshToken.setReplacedByToken(null);
        return refreshToken;
    }

    private RefreshToken getTokenOrThrow(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken is required");
        }

        return refreshTokenRepository.findByToken(refreshToken.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
    }

    private void assertAdminRefreshAllowed(RefreshToken existing, LocalDateTime now) {
        User user = userRepository.findById(existing.getPrincipalId())
                .orElseThrow(() -> new IllegalArgumentException("Admin account not found. Please login again."));

        if (!user.isEnabled()) {
            revokeAllActiveTokens(existing.getPrincipalType(), existing.getPrincipalId(), existing.getClientType(), now);
            throw new IllegalArgumentException("This admin account is disabled. Please login again.");
        }

        if (user.isAdminAccessExpiredAt(now)) {
            revokeAllActiveTokens(existing.getPrincipalType(), existing.getPrincipalId(), existing.getClientType(), now);
            throw new IllegalArgumentException("Admin access time expired. Please contact your super admin.");
        }
    }

    private String generateSecureTokenValue() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
