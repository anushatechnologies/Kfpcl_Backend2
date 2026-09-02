package com.project.Anusha.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.project.Anusha.config.FirebaseConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FirebaseService {

    private static final Pattern PRIVATE_KEY_PATTERN =
            Pattern.compile("\"private_key\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);

    private final FirebaseConfig firebaseConfig;

    public FirebaseService(FirebaseConfig firebaseConfig) {
        this.firebaseConfig = firebaseConfig;
    }

    @PostConstruct
    public void initialize() {
        try {
            String base64Config = System.getenv("FIREBASE_CONFIG_BASE64");
            InputStream credentialsStream;
            String sourceLabel;

            if (base64Config != null && !base64Config.isBlank()) {
                String normalizedConfig = base64Config.trim();

                if (normalizedConfig.startsWith("{")) {
                    normalizedConfig = normalizeFirebaseJson(normalizedConfig);
                    credentialsStream = new ByteArrayInputStream(
                            normalizedConfig.getBytes(StandardCharsets.UTF_8)
                    );
                    sourceLabel = "ENVIRONMENT VARIABLE (Raw JSON)";
                } else {
                    byte[] decodedBytes = java.util.Base64.getMimeDecoder().decode(normalizedConfig);
                    credentialsStream = new ByteArrayInputStream(decodedBytes);
                    sourceLabel = "ENVIRONMENT VARIABLE (Base64)";
                }
            } else {
                String path = firebaseConfig.getServiceAccountPath();
                if (path != null && path.startsWith("classpath:")) {
                    path = path.substring(10);
                }
                ClassPathResource resource = new ClassPathResource(path != null ? path : "");
                if (!resource.exists()) {
                    throw new RuntimeException("Firebase Service Account JSON not found on classpath: " + path);
                }
                credentialsStream = resource.getInputStream();
                sourceLabel = "CLASSPATH FILE (" + (path != null ? path : "null") + ")";
            }

            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setDatabaseUrl(firebaseConfig.getDatabaseUrl())
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("🔥 Firebase initialized successfully from " + sourceLabel);
                
                if (credentials instanceof ServiceAccountCredentials) {
                    System.out.println("✅ Project ID: " + ((ServiceAccountCredentials) credentials).getProjectId());
                }
            } else {
                System.out.println("ℹ️ Firebase already initialized. Skipping.");
            }
        } catch (Exception e) {
            System.err.println("❌ Firebase initialization failed:");
            e.printStackTrace();
            throw new RuntimeException("Firebase init failed", e);
        }
    }

    private String normalizeFirebaseJson(String rawJson) {
        Matcher matcher = PRIVATE_KEY_PATTERN.matcher(rawJson);
        if (!matcher.find()) {
            return rawJson;
        }

        String privateKeyValue = matcher.group(1)
                .replace("\\\r\n", "\\n")
                .replace("\\\n", "\\n")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");

        return matcher.replaceFirst("\"private_key\":\"" + Matcher.quoteReplacement(privateKeyValue) + "\"");
    }

    public FirebaseToken verifyToken(String idToken) throws FirebaseAuthException {
        return FirebaseAuth.getInstance().verifyIdToken(idToken);
    }

    /**
     * Sends a simple push notification to a specific device via FCM token.
     */
    public void sendNotification(String fcmToken, String title, String body) {
        try {
            java.util.Map<String, String> data = new java.util.HashMap<>();
            data.put("type", "system");
            data.put("title", title);
            data.put("body", body);

            com.google.firebase.messaging.Message message = com.google.firebase.messaging.Message.builder()
                    .setToken(fcmToken)
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)
                    .setAndroidConfig(buildAndroidConfig("system", null))
                    .setApnsConfig(buildApnsConfig("system"))
                    .build();

            String response = com.google.firebase.messaging.FirebaseMessaging.getInstance().send(message);
            System.out.println("🚀 Successfully sent message: " + response);
        } catch (Exception e) {
            System.err.println("❌ Failed to send Firebase notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a push notification with custom data.
     */
    public void sendNotificationWithData(String fcmToken, String title, String body, java.util.Map<String, String> data) {
        try {
            java.util.Map<String, String> modifiableData = data != null ? new java.util.HashMap<>(data) : new java.util.HashMap<>();
            String type = modifiableData.getOrDefault("type", "system");
            modifiableData.putIfAbsent("type", type);
            modifiableData.putIfAbsent("title", title);
            modifiableData.putIfAbsent("body", body);

            String imageUrl = modifiableData.get("imageUrl");
            if (imageUrl == null || imageUrl.isBlank()) {
                imageUrl = modifiableData.get("image");
            }

            com.google.firebase.messaging.Notification.Builder notificationBuilder = 
                    com.google.firebase.messaging.Notification.builder()
                            .setTitle(title)
                            .setBody(body);

            if (imageUrl != null && !imageUrl.isBlank()) {
                notificationBuilder.setImage(imageUrl.trim());
            }

            System.out.println("FCM DEBUG: sendNotificationWithData -> imageUrl = " + imageUrl);
            System.out.println("FCM DEBUG: sendNotificationWithData -> payload = " + modifiableData);

            com.google.firebase.messaging.Message message = com.google.firebase.messaging.Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notificationBuilder.build())
                    .putAllData(modifiableData)
                    .setAndroidConfig(buildAndroidConfig(type, imageUrl))
                    .setApnsConfig(buildApnsConfig(type))
                    .build();

            String response = com.google.firebase.messaging.FirebaseMessaging.getInstance().send(message);
            System.out.println("🚀 Successfully sent message with data: " + response);
        } catch (Exception e) {
            System.err.println("❌ Failed to send Firebase notification with data: " + e.getMessage());
        }
    }

    /**
     * Sends a multicast push notification to multiple tokens.
     */
    public void sendMulticastNotification(java.util.List<String> tokens, String title, String body) {
        if (tokens == null || tokens.isEmpty()) return;
        
        try {
            java.util.Map<String, String> data = new java.util.HashMap<>();
            data.put("type", "system");
            data.put("title", title);
            data.put("body", body);

            com.google.firebase.messaging.MulticastMessage message = com.google.firebase.messaging.MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)
                    .setAndroidConfig(buildAndroidConfig("system", null))
                    .setApnsConfig(buildApnsConfig("system"))
                    .build();

            com.google.firebase.messaging.BatchResponse response = com.google.firebase.messaging.FirebaseMessaging.getInstance().sendEachForMulticast(message);
            System.out.println("🚀 Successfully sent multicast message. Success: " + response.getSuccessCount() + ", Failure: " + response.getFailureCount());
        } catch (Exception e) {
            System.err.println("❌ Failed to send Multicast Firebase notification: " + e.getMessage());
        }
    }

    /**
     * Sends a multicast push notification with custom data to multiple tokens.
     */
    public void sendMulticastNotificationWithData(java.util.List<String> tokens, String title, String body, java.util.Map<String, String> data) {
        if (tokens == null || tokens.isEmpty()) return;

        try {
            java.util.Map<String, String> modifiableData = data != null ? new java.util.HashMap<>(data) : new java.util.HashMap<>();
            String type = modifiableData.getOrDefault("type", "system");
            modifiableData.putIfAbsent("type", type);
            modifiableData.putIfAbsent("title", title);
            modifiableData.putIfAbsent("body", body);

            String imageUrl = modifiableData.get("imageUrl");
            if (imageUrl == null || imageUrl.isBlank()) {
                imageUrl = modifiableData.get("image");
            }

            com.google.firebase.messaging.Notification.Builder notificationBuilder = 
                    com.google.firebase.messaging.Notification.builder()
                            .setTitle(title)
                            .setBody(body);

            if (imageUrl != null && !imageUrl.isBlank()) {
                notificationBuilder.setImage(imageUrl.trim());
            }

            System.out.println("FCM DEBUG: sendMulticastNotificationWithData -> imageUrl = " + imageUrl);
            System.out.println("FCM DEBUG: sendMulticastNotificationWithData -> payload = " + modifiableData);

            com.google.firebase.messaging.MulticastMessage message = com.google.firebase.messaging.MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(notificationBuilder.build())
                    .putAllData(modifiableData)
                    .setAndroidConfig(buildAndroidConfig(type, imageUrl))
                    .setApnsConfig(buildApnsConfig(type))
                    .build();

            com.google.firebase.messaging.BatchResponse response = com.google.firebase.messaging.FirebaseMessaging.getInstance().sendEachForMulticast(message);
            System.out.println("🚀 Successfully sent multicast message with data. Success: " + response.getSuccessCount() + ", Failure: " + response.getFailureCount());
        } catch (Exception e) {
            System.err.println("❌ Failed to send Multicast Firebase notification with data: " + e.getMessage());
        }
    }

    /**
     * Updates the delivery person's live GPS location in Firebase RTDB.
     * Path: tracking/{orderNumber}/
     * Customer app listens to this path in real-time.
     */
    public void updateDeliveryLocation(String orderNumber, double lat, double lng,
                                        String status, String deliveryPersonName, String deliveryPersonPhone) {
        try {
            com.google.firebase.database.DatabaseReference ref =
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("tracking")
                            .child(orderNumber);

            java.util.Map<String, Object> locationData = new java.util.HashMap<>();
            locationData.put("lat", lat);
            locationData.put("lng", lng);
            locationData.put("status", status);
            locationData.put("deliveryPersonName", deliveryPersonName != null ? deliveryPersonName : "");
            locationData.put("deliveryPersonPhone", deliveryPersonPhone != null ? deliveryPersonPhone : "");
            locationData.put("updatedAt", java.time.LocalDateTime.now().toString());

            ref.setValueAsync(locationData);
            System.out.println("📍 RTDB location updated for order " + orderNumber + ": " + lat + ", " + lng);
        } catch (Exception e) {
            System.err.println("❌ Failed to update RTDB location: " + e.getMessage());
        }
    }

    /**
     * Updates just the status string in Firebase RTDB (e.g. EN_ROUTE_TO_CUSTOMER, DELIVERED).
     */
    public void updateOrderTrackingStatus(String orderNumber, String status) {
        try {
            com.google.firebase.database.DatabaseReference ref =
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("tracking")
                            .child(orderNumber)
                            .child("status");

            ref.setValueAsync(status);
            System.out.println("🔄 RTDB tracking status updated for order " + orderNumber + ": " + status);
        } catch (Exception e) {
            System.err.println("❌ Failed to update RTDB tracking status: " + e.getMessage());
        }
    }

    /**
     * Writes the delivery partner's approval status to RTDB.
     * App listens to delivery-status/{deliveryPersonId} for instant approval notification.
     */
    public void writeDeliveryPartnerStatus(Long deliveryPersonId, String approvalStatus) {
        try {
            com.google.firebase.database.DatabaseReference ref =
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("delivery-status")
                            .child(String.valueOf(deliveryPersonId));

            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("approvalStatus", approvalStatus);
            data.put("updatedAt", java.time.LocalDateTime.now().toString());

            ref.setValueAsync(data);
            System.out.println("✅ RTDB delivery-status updated for partner " + deliveryPersonId + ": " + approvalStatus);
        } catch (Exception e) {
            System.err.println("❌ Failed to update RTDB delivery-status: " + e.getMessage());
        }
    }

    /**
     * Clears the tracking node from Firebase RTDB when delivery is complete.
     */
    public void clearDeliveryTracking(String orderNumber) {
        try {
            com.google.firebase.database.DatabaseReference ref =
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("tracking")
                            .child(orderNumber);

            ref.removeValueAsync();
            System.out.println("🧹 RTDB tracking cleared for order " + orderNumber);
        } catch (Exception e) {
            System.err.println("❌ Failed to clear RTDB tracking: " + e.getMessage());
        }
    }

    /**
     * Pushes a general event to the admin dashboard node in RTDB.
     * Admin panel listens to /admin/events/ for real-time updates.
     */
    public void pushDashboardEvent(String eventType, Object data) {
        try {
            com.google.firebase.database.DatabaseReference ref =
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("admin")
                            .child("events")
                            .push();

            java.util.Map<String, Object> event = new java.util.HashMap<>();
            event.put("type", eventType);
            event.put("data", data);
            event.put("timestamp", java.time.LocalDateTime.now().toString());

            ref.setValueAsync(event);
            System.out.println("📢 Dashboard event pushed: " + eventType);
        } catch (Exception e) {
            System.err.println("❌ Failed to push dashboard event: " + e.getMessage());
        }
    }

    private com.google.firebase.messaging.AndroidConfig buildAndroidConfig(String type, String imageUrl) {
        String androidSound;
        String channelId;
        switch (type) {
            case "order": androidSound = "order_placed"; channelId = "ORDER_CHANNEL"; break;
            case "delivery": androidSound = "order_delivered"; channelId = "DELIVERY_CHANNEL"; break;
            case "promo": androidSound = "offer_alert"; channelId = "OFFER_CHANNEL"; break;
            case "wallet":
            case "message": androidSound = "message_tone"; channelId = "MESSAGE_CHANNEL"; break;
            default: androidSound = "default"; channelId = "DEFAULT_CHANNEL"; break;
        }
        com.google.firebase.messaging.AndroidNotification.Builder androidNotifBuilder = 
                com.google.firebase.messaging.AndroidNotification.builder()
                        .setSound(androidSound)
                        .setChannelId(channelId)
                        .setPriority(com.google.firebase.messaging.AndroidNotification.Priority.HIGH);
        
        if (imageUrl != null && !imageUrl.isBlank()) {
            androidNotifBuilder.setImage(imageUrl.trim());
        }

        return com.google.firebase.messaging.AndroidConfig.builder()
                .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                .setNotification(androidNotifBuilder.build())
                .build();
    }

    private com.google.firebase.messaging.ApnsConfig buildApnsConfig(String type) {
        String iosSound;
        switch (type) {
            case "order": iosSound = "order_placed.wav"; break;
            case "delivery": iosSound = "order_delivered.wav"; break;
            case "promo": iosSound = "offer_alert.wav"; break;
            case "wallet":
            case "message": iosSound = "message_tone.wav"; break;
            default: iosSound = "default"; break;
        }
        return com.google.firebase.messaging.ApnsConfig.builder()
                .setAps(com.google.firebase.messaging.Aps.builder()
                        .setSound(iosSound)
                        .setBadge(1)
                        .setContentAvailable(true)
                        .build())
                .build();
    }
}
