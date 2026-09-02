package com.project.Anusha.service;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.admin-panel.base-url}")
    private String adminPanelBaseUrl;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name:Anusha Bazaar}")
    private String fromName;

    @Value("${app.mail.reply-to:}")
    private String replyToAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public String generateSystemPassword() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    public void sendAdminSetupEmail(String email, String displayName) {
        sendAdminPasswordActionEmail(email, displayName, true);
    }

    public void sendAdminPasswordResetEmail(String email, String displayName) {
        sendAdminPasswordActionEmail(email, displayName, false);
    }

    public void ensureFirebaseUserReadyForPasswordReset(String email, String displayName) {
        try {
            UserRecord existing = findFirebaseUserByEmail(email);
            if (existing == null) {
                createFirebaseUser(email, displayName, generateSystemPassword(), false);
                return;
            }

            UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(existing.getUid())
                    .setDisabled(false);

            if (hasText(displayName)) {
                request.setDisplayName(displayName.trim());
            }

            FirebaseAuth.getInstance().updateUser(request);
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Failed to prepare Firebase reset user: " + e.getMessage(), e);
        }
    }

    public void ensureFirebaseUserExists(String email, String displayName, String rawPassword) {
        try {
            UserRecord existing = findFirebaseUserByEmail(email);
            if (existing == null) {
                createFirebaseUser(email, displayName, rawPassword, false);
                return;
            }

            UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(existing.getUid())
                    .setPassword(rawPassword)
                    .setDisabled(false);

            if (hasText(displayName)) {
                request.setDisplayName(displayName.trim());
            }

            FirebaseAuth.getInstance().updateUser(request);
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Failed to prepare Firebase auth user: " + e.getMessage(), e);
        }
    }

    public void resetFirebasePassword(String email, String displayName, String rawPassword, boolean disabled) {
        try {
            UserRecord existing = findFirebaseUserByEmail(email);
            if (existing == null) {
                createFirebaseUser(email, displayName, rawPassword, disabled);
                return;
            }

            UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(existing.getUid())
                    .setPassword(rawPassword)
                    .setDisabled(disabled);

            if (hasText(displayName)) {
                request.setDisplayName(displayName.trim());
            }

            FirebaseAuth.getInstance().updateUser(request);
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Failed to reset Firebase password: " + e.getMessage(), e);
        }
    }

    public void syncFirebasePassword(String email, String displayName, String rawPassword) {
        resetFirebasePassword(email, displayName, rawPassword, false);
    }

    public void updateFirebaseUserDisabled(String email, boolean disabled) {
        try {
            UserRecord existing = findFirebaseUserByEmail(email);
            if (existing == null) {
                return;
            }

            FirebaseAuth.getInstance().updateUser(
                    new UserRecord.UpdateRequest(existing.getUid()).setDisabled(disabled)
            );
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Failed to update Firebase account status: " + e.getMessage(), e);
        }
    }

    private void sendAdminPasswordActionEmail(String email, String displayName, boolean firstTimeSetup) {
        validateMailConfiguration();

        try {
            String firebaseLink = FirebaseAuth.getInstance().generatePasswordResetLink(email);
            String adminResetLink = buildAdminResetLink(firebaseLink);
            sendResetEmailMessage(email, displayName, adminResetLink, firstTimeSetup);
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Failed to generate Firebase reset link: " + e.getMessage(), e);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            throw new RuntimeException("Failed to send admin email: " + e.getMessage(), e);
        }
    }

    private void sendResetEmailMessage(String email, String displayName, String resetLink, boolean firstTimeSetup)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());

        helper.setTo(email);
        helper.setFrom(fromAddress, fromName);

        if (hasText(replyToAddress)) {
            helper.setReplyTo(replyToAddress.trim());
        }

        helper.setSubject(firstTimeSetup
                ? "Set up your Anusha Bazaar admin password"
                : "Reset your Anusha Bazaar admin password");
        helper.setText(buildResetEmailHtml(email, displayName, resetLink, firstTimeSetup), true);

        mailSender.send(message);
    }

    private String buildResetEmailHtml(String email, String displayName, String resetLink, boolean firstTimeSetup) {
        String safeName = hasText(displayName) ? HtmlUtils.htmlEscape(displayName.trim()) : "Admin";
        String safeEmail = HtmlUtils.htmlEscape(email);
        String safeLink = HtmlUtils.htmlEscape(resetLink);
        String actionLabel = firstTimeSetup ? "Set Password" : "Reset Password";
        String intro = firstTimeSetup
                ? "Your admin account is ready. Click the button below to set your password and open the admin panel."
                : "We received a request to reset your admin password. Click the button below to choose a new password.";
        String supportAddress = hasText(replyToAddress) ? replyToAddress.trim() : fromAddress;

        return """
                <div style="font-family:Arial,Helvetica,sans-serif;background:#f7f8fc;padding:24px;color:#1f2937;">
                  <div style="max-width:640px;margin:0 auto;background:#ffffff;border-radius:16px;padding:32px;border:1px solid #e5e7eb;">
                    <p style="margin:0 0 16px;font-size:15px;">Hello %s,</p>
                    <p style="margin:0 0 16px;font-size:15px;line-height:1.7;">%s</p>
                    <p style="margin:0 0 24px;font-size:14px;color:#4b5563;">Account: <strong>%s</strong></p>
                    <p style="margin:0 0 28px;">
                      <a href="%s" style="display:inline-block;background:#1d4ed8;color:#ffffff;text-decoration:none;padding:14px 24px;border-radius:10px;font-weight:700;">%s</a>
                    </p>
                    <p style="margin:0 0 12px;font-size:13px;color:#6b7280;line-height:1.7;">If the button does not open, copy and paste this link into your browser:</p>
                    <p style="margin:0 0 24px;font-size:13px;word-break:break-all;"><a href="%s">%s</a></p>
                    <p style="margin:0 0 8px;font-size:13px;color:#6b7280;">If you did not request this action, you can ignore this email.</p>
                    <p style="margin:0;font-size:13px;color:#6b7280;">Need help? Reply to %s</p>
                  </div>
                </div>
                """.formatted(
                safeName,
                HtmlUtils.htmlEscape(intro),
                safeEmail,
                safeLink,
                actionLabel,
                safeLink,
                safeLink,
                HtmlUtils.htmlEscape(supportAddress)
        );
    }

    private String buildAdminResetLink(String firebaseLink) {
        var queryParams = UriComponentsBuilder.fromUriString(firebaseLink).build().getQueryParams();
        String nestedLink = queryParams.getFirst("link");

        if (hasText(nestedLink)) {
            queryParams = UriComponentsBuilder.fromUriString(nestedLink).build().getQueryParams();
        }

        String oobCode = queryParams.getFirst("oobCode");
        String mode = queryParams.getFirst("mode");
        String apiKey = queryParams.getFirst("apiKey");
        String lang = queryParams.getFirst("lang");

        if (!hasText(oobCode) || !hasText(mode)) {
            throw new RuntimeException("Firebase reset link did not include the expected parameters");
        }

        UriComponentsBuilder resetLinkBuilder = UriComponentsBuilder.fromUriString(trimTrailingSlash(adminPanelBaseUrl) + "/reset-password")
                .queryParam("mode", mode)
                .queryParam("oobCode", oobCode);

        if (hasText(apiKey)) {
            resetLinkBuilder.queryParam("apiKey", apiKey);
        }
        if (hasText(lang)) {
            resetLinkBuilder.queryParam("lang", lang);
        }

        return resetLinkBuilder.build(true).toUriString();
    }

    private String trimTrailingSlash(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void validateMailConfiguration() {
        if (!hasText(adminPanelBaseUrl)) {
            throw new RuntimeException("ADMIN_PANEL_BASE_URL is not configured");
        }
        if (!hasText(fromAddress)) {
            throw new RuntimeException("MAIL_FROM is not configured");
        }
        if (mailSender instanceof JavaMailSenderImpl sender && !hasText(sender.getHost())) {
            throw new RuntimeException("MAIL_HOST is not configured");
        }
    }

    private void createFirebaseUser(String email, String displayName, String rawPassword, boolean disabled)
            throws FirebaseAuthException {
        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(rawPassword)
                .setDisabled(disabled);

        if (hasText(displayName)) {
            request.setDisplayName(displayName.trim());
        }

        FirebaseAuth.getInstance().createUser(request);
    }

    private UserRecord findFirebaseUserByEmail(String email) throws FirebaseAuthException {
        try {
            return FirebaseAuth.getInstance().getUserByEmail(email);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
