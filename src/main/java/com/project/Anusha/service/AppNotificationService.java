package com.project.Anusha.service;

import com.project.Anusha.model.AppNotification;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.repository.AppNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class AppNotificationService {

    private final AppNotificationRepository appNotificationRepository;
    private final UserMainService userMainService;
    private final FirebaseService firebaseService;

    public AppNotificationService(
            AppNotificationRepository appNotificationRepository,
            UserMainService userMainService,
            FirebaseService firebaseService
    ) {
        this.appNotificationRepository = appNotificationRepository;
        this.userMainService = userMainService;
        this.firebaseService = firebaseService;
    }

    public List<Map<String, Object>> getNotificationsForUser(UserMain userMain) {
        if (userMain == null || userMain.getId() == null) {
            return List.of();
        }

        return appNotificationRepository.findByUserMainIdOrderByCreatedAtDesc(userMain.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void markAllRead(UserMain userMain) {
        if (userMain == null || userMain.getId() == null) {
            return;
        }

        List<AppNotification> notifications =
                appNotificationRepository.findByUserMainIdOrderByCreatedAtDesc(userMain.getId());

        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;

        for (AppNotification notification : notifications) {
            if (!notification.isRead()) {
                notification.setRead(true);
                notification.setReadAt(now);
                changed = true;
            }
        }

        if (changed) {
            appNotificationRepository.saveAll(notifications);
        }
    }

    public Map<String, Object> markRead(UserMain userMain, Long notificationId) {
        AppNotification notification = appNotificationRepository
                .findByIdAndUserMainId(notificationId, userMain.getId())
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            appNotificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    public AppNotification notifyUser(
            UserMain userMain,
            String type,
            String screen,
            String title,
            String message,
            Long orderId,
            String orderNumber,
            String targetId
    ) {
        return notifyUser(userMain, type, screen, title, message, orderId, orderNumber, targetId, Collections.emptyMap());
    }

    public AppNotification notifyUser(
            UserMain userMain,
            String type,
            String screen,
            String title,
            String message,
            Long orderId,
            String orderNumber,
            String targetId,
            Map<String, String> pushExtras
    ) {
        if (userMain == null || userMain.getId() == null) {
            throw new IllegalArgumentException("Target user is required");
        }

        AppNotification notification = new AppNotification();
        notification.setUserMain(userMain);
        notification.setType(safeOrDefault(type, "system"));
        notification.setScreen(screen);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setOrderId(orderId);
        notification.setOrderNumber(orderNumber);
        notification.setTargetId(targetId);

        AppNotification saved = appNotificationRepository.save(notification);
        sendPush(saved, pushExtras);
        return saved;
    }

    public List<AppNotification> notifyUsers(
            List<UserMain> users,
            String type,
            String screen,
            String title,
            String message,
            Long orderId,
            String orderNumber,
            String targetId
    ) {
        return notifyUsers(users, type, screen, title, message, orderId, orderNumber, targetId, Collections.emptyMap());
    }

    public List<AppNotification> notifyUsers(
            List<UserMain> users,
            String type,
            String screen,
            String title,
            String message,
            Long orderId,
            String orderNumber,
            String targetId,
            Map<String, String> pushExtras
    ) {
        List<AppNotification> notifications = new ArrayList<>();
        if (users == null || users.isEmpty()) {
            return notifications;
        }

        for (UserMain user : users) {
            if (user == null || user.getId() == null) {
                continue;
            }
            notifications.add(notifyUser(user, type, screen, title, message, orderId, orderNumber, targetId, pushExtras));
        }

        return notifications;
    }

    private void sendPush(AppNotification notification) {
        sendPush(notification, Collections.emptyMap());
    }

    private void sendPush(AppNotification notification, Map<String, String> pushExtras) {
        try {
            List<String> tokens = userMainService.getFcmTokensForUser(notification.getUserMain());
            if (tokens.isEmpty()) {
                return;
            }

            Map<String, String> data = new HashMap<>();
            data.put("notificationId", String.valueOf(notification.getId()));
            data.put("type", safeOrDefault(notification.getType(), "system"));
            data.put("title", safeOrDefault(notification.getTitle(), "Notification"));
            data.put("body", safeOrDefault(notification.getMessage(), ""));
            if (notification.getScreen() != null) {
                data.put("screen", notification.getScreen());
            }
            if (notification.getOrderId() != null) {
                data.put("orderId", String.valueOf(notification.getOrderId()));
            }
            if (notification.getOrderNumber() != null) {
                data.put("orderNumber", notification.getOrderNumber());
            }
            if (notification.getTargetId() != null) {
                data.put("targetId", notification.getTargetId());
            }
            String deepLink = buildDeepLink(notification);
            if (deepLink != null) {
                data.put("deepLink", deepLink);
                data.put("deeplink", deepLink);
            }
            if (pushExtras != null && !pushExtras.isEmpty()) {
                pushExtras.forEach((key, value) -> {
                    if (key != null && value != null) {
                        data.put(key, value);
                    }
                });
            }

            firebaseService.sendMulticastNotificationWithData(
                    tokens,
                    notification.getTitle(),
                    notification.getMessage(),
                    data
            );
        } catch (Exception exception) {
            System.err.println("Failed to send persisted notification push: " + exception.getMessage());
        }
    }

    private Map<String, Object> toResponse(AppNotification notification) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", notification.getId());
        response.put("type", notification.getType());
        response.put("screen", notification.getScreen());
        response.put("title", notification.getTitle());
        response.put("message", notification.getMessage());
        response.put("body", notification.getMessage());
        response.put("orderId", notification.getOrderId());
        response.put("orderNumber", notification.getOrderNumber());
        response.put("targetId", notification.getTargetId());
        response.put("deepLink", buildDeepLink(notification));
        response.put("deeplink", buildDeepLink(notification));
        response.put("read", notification.isRead());
        response.put("isRead", notification.isRead());
        response.put("createdAt", notification.getCreatedAt());
        response.put("readAt", notification.getReadAt());
        return response;
    }

    private String safeOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * Deep link payload for client navigation. Kept as a simple string for FCM data.
     * Examples:
     *  - order/123
     *  - product/456
     *  - offers
     */
    private String buildDeepLink(AppNotification notification) {
        if (notification == null) {
            return null;
        }
        String screen = notification.getScreen();
        if (screen == null || screen.isBlank()) {
            return null;
        }
        String normalized = screen.trim().toLowerCase();
        if ((normalized.contains("order") || "orders".equals(normalized)) && notification.getOrderId() != null) {
            return "order/" + notification.getOrderId();
        }
        if ((normalized.contains("product") || normalized.contains("offer")) && notification.getTargetId() != null) {
            return "product/" + notification.getTargetId();
        }
        if (notification.getTargetId() != null && !notification.getTargetId().isBlank()) {
            return normalized + "/" + notification.getTargetId().trim();
        }
        return normalized;
    }
}
