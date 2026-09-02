package com.project.Anusha.controller;

import com.project.Anusha.service.DeliveryOrderService;
import com.project.Anusha.service.WhatsAppCampaignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/webhooks")
public class WhatsAppWebhookController {

    @Autowired
    private DeliveryOrderService deliveryOrderService;

    @Autowired
    private WhatsAppCampaignService whatsAppCampaignService;

    @Value("${whatsapp.verify-token:ANUSHA_SECRET_TOKEN}")
    private String verifyToken;

    @Value("${app.orders.operator-whatsapp:919948598350}")
    private String operatorWhatsapp;

    /**
     * Webhook verification for Meta WhatsApp API Setup.
     */
    @GetMapping("/whatsapp")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            System.out.println("✅ WhatsApp Webhook Verified!");
            return ResponseEntity.ok(challenge);
        }
        
        return ResponseEntity.status(403).body("Verification failed");
    }

    /**
     * Webhook for receiving WhatsApp messages from Meta.
     */
    @PostMapping("/whatsapp")
    public ResponseEntity<Void> handleWhatsAppMessage(@RequestBody Map<String, Object> payload) {
        try {
            System.out.println("📩 Received WhatsApp Webhook Payload: " + payload);

            // Navigate through the Meta JSON structure
            List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
            if (entries != null) {
                for (Map<String, Object> entry : entries) {
                    List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
                    if (changes != null) {
                        for (Map<String, Object> change : changes) {
                            Map<String, Object> value = (Map<String, Object>) change.get("value");
                            List<Map<String, Object>> messages = (List<Map<String, Object>>) value.get("messages");
                            if (messages != null) {
                                for (Map<String, Object> msg : messages) {
                                    processMessage(msg);
                                }
                            }

                            List<Map<String, Object>> statuses = (List<Map<String, Object>>) value.get("statuses");
                            if (statuses != null) {
                                for (Map<String, Object> status : statuses) {
                                    processStatus(status);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error processing Meta WhatsApp Webhook: " + e.getMessage());
        }
        
        // Always return 200 to acknowledge receipt
        return ResponseEntity.ok().build();
    }

    private void processMessage(Map<String, Object> msg) {
        String from = (String) msg.get("from");
        String type = (String) msg.get("type");

        if ("text".equals(type)) {
            // Store replied by typing: "ACCEPT ORD-001" or "REJECT ORD-001"
            Map<String, Object> text = (Map<String, Object>) msg.get("text");
            String body = (String) text.get("body");
            handleTextCommand(from, body);

        } else if ("button".equals(type)) {
            // Store tapped a Quick Reply button on the order_notification template.
            // Payload format:
            // - "ACCEPT_SUBORDER_123" / "REJECT_SUBORDER_123"
            // - legacy fallback: "ACCEPT_ORD-001" / "REJECT_ORD-001"
            Map<String, Object> button = (Map<String, Object>) msg.get("button");
            String payloadStr = (String) button.get("payload");
            if (!handleStructuredCommand(from, payloadStr, "Updated via WhatsApp button")) {
                // Fallback: parse the button text as a plain command
                handleTextCommand(from, payloadStr != null ? payloadStr : "");
            }

        } else if ("interactive".equals(type)) {
            // Handles interactive button_reply (non-template interactive messages)
            Map<String, Object> interactive = (Map<String, Object>) msg.get("interactive");
            String interactiveType = (String) interactive.get("type");
            if ("button_reply".equals(interactiveType)) {
                Map<String, Object> buttonReply = (Map<String, Object>) interactive.get("button_reply");
                String id = (String) buttonReply.get("id");
                handleStructuredCommand(from, id, "Updated via WhatsApp button");
            }
        }
    }

    private void processStatus(Map<String, Object> status) {
        String messageId = (String) status.get("id");
        String statusValue = (String) status.get("status");
        String detail = "Meta status: " + statusValue;

        List<Map<String, Object>> errors = (List<Map<String, Object>>) status.get("errors");
        if (errors != null && !errors.isEmpty()) {
            Map<String, Object> firstError = errors.get(0);
            Object code = firstError.get("code");
            Object title = firstError.get("title");
            Object message = firstError.get("message");
            detail = "Meta error"
                    + (code != null ? " " + code : "")
                    + (title != null ? ": " + title : "")
                    + (message != null ? " - " + message : "");
        }

        whatsAppCampaignService.recordMetaStatus(messageId, statusValue, detail);
    }

    private boolean handleStructuredCommand(String from, String payload, String remarks) {
        if (payload == null || payload.isBlank()) {
            return false;
        }

        if (payload.startsWith("ACCEPT_SUBORDER_") || payload.startsWith("REJECT_SUBORDER_")) {
            boolean isAccept = payload.startsWith("ACCEPT_SUBORDER_");
            String subOrderIdStr = payload.substring(payload.lastIndexOf('_') + 1);
            try {
                Long subOrderId = Long.parseLong(subOrderIdStr);
                String action = isAccept ? "ACCEPT" : "REJECT";
                System.out.println("🤖 Store sub-order reply — action: " + action
                        + " subOrderId: " + subOrderId + " from: " + from);
                deliveryOrderService.handleStoreSubOrderResponse(subOrderId, action, remarks);
                return true;
            } catch (NumberFormatException ex) {
                System.err.println("❌ Invalid sub-order payload: " + payload);
                return false;
            }
        }

        if (payload.contains("_")) {
            int sep = payload.indexOf('_');
            String action = payload.substring(0, sep).toUpperCase();
            String orderNumber = payload.substring(sep + 1);
            System.out.println("🤖 Legacy store reply — action: " + action + " order: " + orderNumber + " from: " + from);
            if ("ACCEPT".equals(action) || "REJECT".equals(action)) {
                if (!isOperator(from)) return false;
                deliveryOrderService.handleStoreResponse(orderNumber, action, remarks);
                return true;
            }
        }

        return false;
    }

    private void handleTextCommand(String from, String body) {
        if (body == null || body.isBlank()) return;
        String message = body.trim().toUpperCase();
        System.out.println("🤖 Processing text command: " + message + " from: " + from);

        // Expected format: "ACCEPT ORD-001"  or  "REJECT ORD-001 <optional reason>"
        String[] parts = message.split("\\s+");
        if (parts.length >= 2) {
            String action = parts[0];
            String orderNumber = parts[1];
            String remarks = parts.length > 2
                    ? body.substring(body.toUpperCase().indexOf(parts[2]))
                    : "Updated via WhatsApp";
            if ("ACCEPT".equals(action) || "REJECT".equals(action)) {
                if (!isOperator(from)) return;
                deliveryOrderService.handleStoreResponse(orderNumber, action, remarks);
            }
        }
    }

    private boolean isOperator(String from) {
        if (from == null || operatorWhatsapp == null || operatorWhatsapp.isBlank()) return false;
        String normalizedFrom = from.replaceAll("[^0-9]", "");
        String normalizedOperator = operatorWhatsapp.replaceAll("[^0-9]", "");
        return normalizedFrom.equals(normalizedOperator)
                || (normalizedFrom.length() == 10 && ("91" + normalizedFrom).equals(normalizedOperator));
    }
}
