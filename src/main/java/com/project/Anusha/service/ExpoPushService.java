package com.project.Anusha.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the Expo Push HTTP API.
 *
 *   Endpoint: POST https://exp.host/--/api/v2/push/send
 *
 * Always sends as a batched array (Expo allows up to 100 messages per request).
 * We split callers' lists into chunks of 100 to stay inside that limit.
 *
 * Per-recipient errors are surfaced in the response body — we parse them and
 * deactivate tokens that Expo reports as `DeviceNotRegistered` so stale tokens
 * don't pile up in the DB.
 *
 * The send is `@Async` so order creation isn't blocked by Expo's HTTP latency.
 */
@Service
public class ExpoPushService {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushService.class);
    private static final String EXPO_URL = "https://exp.host/--/api/v2/push/send";
    private static final int MAX_BATCH = 100;

    private final RestTemplate restTemplate;
    private final AdminPushTokenService adminPushTokenService;

    public ExpoPushService(RestTemplate restTemplate, AdminPushTokenService adminPushTokenService) {
        this.restTemplate = restTemplate;
        this.adminPushTokenService = adminPushTokenService;
    }

    /**
     * Broadcast a "new order" notification to every supplied admin token.
     * Runs asynchronously so the caller (e.g. OrderService.placeOrder) returns
     * immediately to the customer regardless of Expo's response time.
     */
    @Async
    public void sendNewOrderBroadcast(List<String> tokens, Long orderId, String title, String body) {
        if (tokens == null || tokens.isEmpty()) return;

        List<Map<String, Object>> messages = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            if (token == null || token.isBlank()) continue;
            Map<String, Object> message = new HashMap<>();
            message.put("to", token);
            message.put("title", title);
            message.put("body", body);
            message.put("sound", "default");
            message.put("priority", "high");
            message.put("channelId", "new-orders"); // Android channel registered by the app
            message.put("data", Map.of("orderId", orderId, "type", "NEW_ORDER"));
            messages.add(message);
        }
        sendBatched(messages, tokens);
    }

    private void sendBatched(List<Map<String, Object>> messages, List<String> originalTokens) {
        for (int i = 0; i < messages.size(); i += MAX_BATCH) {
            int end = Math.min(i + MAX_BATCH, messages.size());
            List<Map<String, Object>> slice = messages.subList(i, end);
            List<String> tokenSlice = originalTokens.subList(i, end);
            try {
                postBatch(slice, tokenSlice);
            } catch (Exception e) {
                log.warn("Expo push batch [{}..{}) failed: {}", i, end, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void postBatch(List<Map<String, Object>> messages, List<String> tokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("accept", "application/json");
        headers.set("accept-encoding", "gzip, deflate");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                EXPO_URL,
                new HttpEntity<>(messages, headers),
                Map.class);

        if (response.getBody() == null) return;
        Object data = response.getBody().get("data");
        if (!(data instanceof List<?> tickets)) return;

        // Tickets come back in the same order as the messages we sent.
        for (int i = 0; i < tickets.size() && i < tokens.size(); i++) {
            Object ticketObj = tickets.get(i);
            if (!(ticketObj instanceof Map<?, ?> ticket)) continue;
            Object status = ticket.get("status");
            if (!"error".equals(status)) continue;

            Object details = ticket.get("details");
            String errCode = null;
            if (details instanceof Map<?, ?> d) {
                Object e = d.get("error");
                if (e != null) errCode = e.toString();
            }
            String token = tokens.get(i);
            if ("DeviceNotRegistered".equals(errCode)) {
                log.info("Expo reports DeviceNotRegistered for token {}, deactivating", maskToken(token));
                adminPushTokenService.markInactiveOnExpoError(token);
            } else {
                log.warn("Expo push error for token {}: {}", maskToken(token), errCode != null ? errCode : ticket.get("message"));
            }
        }
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 20) return "***";
        return token.substring(0, 12) + "..." + token.substring(token.length() - 4);
    }
}
