package com.project.Anusha.controller;

import com.project.Anusha.model.UserMain;
import com.project.Anusha.repository.UserMainRepository;
import com.project.Anusha.service.AppNotificationService;
import com.project.Anusha.service.FirebaseService;
import com.project.Anusha.service.UserMainService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final UserMainService userMainService;
    private final UserMainRepository userMainRepository;
    private final FirebaseService firebaseService;
    private final AppNotificationService appNotificationService;

    public NotificationController(UserMainService userMainService,
                                  UserMainRepository userMainRepository,
                                  FirebaseService firebaseService,
                                  AppNotificationService appNotificationService) {
        this.userMainService = userMainService;
        this.userMainRepository = userMainRepository;
        this.firebaseService = firebaseService;
        this.appNotificationService = appNotificationService;
    }

    /**
     * Endpoint to save or update FCM token for a user.
     * POST /api/save-token
     */
    @PostMapping("/save-token")
    public ResponseEntity<?> saveToken(@RequestBody Map<String, String> req) {
        String phone = req.get("phone");
        String token = req.get("fcmToken");

        if (phone == null || token == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone and fcmToken are required"));
        }

        userMainService.updateFcmToken(phone, token);
        return ResponseEntity.ok(Map.of("success", true, "message", "Token saved successfully"));
    }

    /**
     * Returns the authenticated user's persisted notification inbox.
     */
    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        UserMain currentUser = resolveAuthenticatedUser(userDetails);
        if (currentUser == null) {
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(appNotificationService.getNotificationsForUser(currentUser));
    }

    /**
     * Marks every persisted notification as read for the authenticated user.
     */
    @PatchMapping("/notifications/read-all")
    public ResponseEntity<?> markAllNotificationsRead(@AuthenticationPrincipal UserDetails userDetails) {
        UserMain currentUser = resolveAuthenticatedUser(userDetails);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Authenticated mobile user not found"));
        }

        appNotificationService.markAllRead(currentUser);
        return ResponseEntity.ok(Map.of("success", true, "message", "Notifications marked as read"));
    }

    /**
     * Marks one persisted notification as read for the authenticated user.
     */
    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<?> markNotificationRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserMain currentUser = resolveAuthenticatedUser(userDetails);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Authenticated mobile user not found"));
        }

        try {
            Map<String, Object> notification = appNotificationService.markRead(currentUser, notificationId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Notification marked as read",
                    "notification", notification
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", exception.getMessage()));
        }
    }

    /**
     * Admin endpoint to send notification to a specific phone number.
     * POST /api/admin/notifications/send
     */
    @PostMapping("/admin/notifications/send")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, String> req) {
        String phoneInput = req.get("phoneNumber"); // Frontend uses phoneNumber
        if (phoneInput == null) phoneInput = req.get("phone");
        
        String type = req.getOrDefault("type", "system");
        String screen = req.get("screen");
        Long orderId = parseLong(req.get("orderId"));
        String orderNumber = req.get("orderNumber");
        String targetId = req.get("targetId");

        String title = req.get("title");
        String messageContent = req.get("message"); // Frontend uses message
        if (messageContent == null) messageContent = req.get("body");

        if (phoneInput == null || title == null || messageContent == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumber, title, and message are required"));
        }

        String phone = phoneInput;
        if (!phone.startsWith("+")) {
            if (phone.length() == 10) phone = "+91" + phone;
        }

        var userOpt = userMainRepository.findByPhoneNumber(phone);
        if (userOpt.isPresent()) {
            UserMain user = userOpt.get();
            int deviceCount = userMainService.getFcmTokensForUser(user).size();

            String imageUrl = req.get("imageUrl");
            java.util.Map<String, String> pushExtras = new java.util.HashMap<>();
            if (imageUrl != null && !imageUrl.isBlank()) {
                pushExtras.put("imageUrl", imageUrl.trim());
            }

            appNotificationService.notifyUser(
                    user,
                    type,
                    screen != null ? screen : "Notifications",
                    title,
                    messageContent,
                    orderId,
                    orderNumber,
                    targetId,
                    pushExtras
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", deviceCount > 0
                            ? "Notification saved and sent to " + deviceCount + " devices"
                            : "Notification saved to inbox. No active device tokens were registered",
                    "deviceCount", deviceCount
            ));
        }

        return ResponseEntity.status(404).body(Map.of("error", "User not found with phone " + phone));
    }

    /**
     * Admin endpoint to send notification to ALL CUSTOMERS.
     * POST /api/admin/notifications/send-to-customers
     */
    @PostMapping("/admin/notifications/send-to-customers")
    public ResponseEntity<?> sendToAllCustomers(@RequestBody Map<String, String> req) {
        String type = req.getOrDefault("type", "broadcast");
        String screen = req.get("screen");
        Long orderId = parseLong(req.get("orderId"));
        String orderNumber = req.get("orderNumber");
        String targetId = req.get("targetId");

        String title = req.get("title");
        String messageContent = req.get("message");
        if (messageContent == null) messageContent = req.get("body");
        if (title == null || messageContent == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "title and message are required"));
        }

        List<UserMain> customers = userMainRepository.findAll().stream()
                .filter(u -> u.hasRole("CUSTOMER"))
                .collect(Collectors.toList());

        int inboxSaved = 0;
        int totalSent = 0;
        String imageUrl = req.get("imageUrl");
        java.util.Map<String, String> pushExtras = new java.util.HashMap<>();
        if (imageUrl != null && !imageUrl.isBlank()) {
            pushExtras.put("imageUrl", imageUrl.trim());
        }

        for (UserMain customer : customers) {
            totalSent += userMainService.getFcmTokensForUser(customer).size();
            appNotificationService.notifyUser(
                    customer,
                    type,
                    screen != null ? screen : "Notifications",
                    title,
                    messageContent,
                    orderId,
                    orderNumber,
                    targetId,
                    pushExtras
            );
            inboxSaved++;
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Broadcast saved for " + inboxSaved + " customers and sent to " + totalSent + " devices",
                "recipients", inboxSaved,
                "deviceCount", totalSent
        ));
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Admin endpoint to send notification to ALL DELIVERY PERSONS.
     * POST /api/admin/notifications/send-to-delivery
     */
    @PostMapping("/admin/notifications/send-to-delivery")
    public ResponseEntity<?> sendToAllDelivery(@RequestBody Map<String, String> req) {
        String title = req.get("title");
        String messageContent = req.get("message");
        if (messageContent == null) messageContent = req.get("body");
        if (title == null || messageContent == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "title and message are required"));
        }

        List<UserMain> deliveryPersons = userMainRepository.findAll().stream()
                .filter(u -> u.hasRole("DELIVERY_PERSON"))
                .collect(Collectors.toList());

        int inboxSaved = 0;
        int totalSent = 0;
        String imageUrl = req.get("imageUrl");
        java.util.Map<String, String> pushExtras = new java.util.HashMap<>();
        if (imageUrl != null && !imageUrl.isBlank()) {
            pushExtras.put("imageUrl", imageUrl.trim());
        }

        for (UserMain dp : deliveryPersons) {
            totalSent += userMainService.getFcmTokensForUser(dp).size();
            appNotificationService.notifyUser(
                    dp,
                    "broadcast",
                    "DeliveryNotifications",
                    title,
                    messageContent,
                    null,
                    null,
                    null,
                    pushExtras
            );
            inboxSaved++;
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Broadcast saved for " + inboxSaved + " delivery partners and sent to " + totalSent + " devices",
                "recipients", inboxSaved,
                "deviceCount", totalSent
        ));
    }
    /**
     * Admin endpoint to send notification directly to a RAW FCM TOKEN.
     * POST /api/admin/notifications/send-by-token
     */
    @PostMapping("/admin/notifications/send-by-token")
    public ResponseEntity<?> sendByToken(@RequestBody Map<String, String> req) {
        String token = req.get("token");
        String title = req.get("title");
        String messageContent = req.get("message");
        if (messageContent == null) messageContent = req.get("body");

        if (token == null || title == null || messageContent == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "token, title, and message are required"));
        }

        firebaseService.sendNotification(token, title, messageContent);
        return ResponseEntity.ok(Map.of("success", true, "message", "Notification sent successfully to token"));
    }

    private UserMain resolveAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            return null;
        }

        String username = userDetails.getUsername().trim();
        UserMain user = userMainService.findByPhoneNumber(username).orElse(null);
        if (user != null) {
            return user;
        }

        if (!username.startsWith("+") && username.length() == 10) {
            return userMainService.findByPhoneNumber("+91" + username).orElse(null);
        }

        if (username.startsWith("+91") && username.length() == 13) {
            return userMainService.findByPhoneNumber(username.substring(3)).orElse(null);
        }

        return null;
    }
}
