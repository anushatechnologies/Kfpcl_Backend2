package com.project.Anusha.service;

import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.model.Order;
import com.project.Anusha.model.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Collections;

@Service
public class WhatsAppService {
    private static final long WHATSAPP_VIDEO_MAX_BYTES = 16L * 1024L * 1024L;

    @Value("${whatsapp.api.url}")
    private String apiUrl;

    @Value("${whatsapp.api.token}")
    private String apiToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.waba-id:}")
    private String wabaId;

    @Value("${whatsapp.template-language-code:en_US}")
    private String templateLanguageCode;

    @Value("${whatsapp.customer-order-confirmed-template:customer_order_confirmed}")
    private String customerOrderConfirmedTemplate;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WhatsAppService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a WhatsApp message to the store about a new order.
     */
    public void notifyStoreOfNewOrder(DeliveryOrder deliveryOrder, Order order) {
        String storePhone = deliveryOrder.getStore().getPhoneNumber();
        if (storePhone == null || storePhone.isEmpty()) {
            System.err.println("❌ Store phone number missing for order: " + deliveryOrder.getOrderNumber());
            return;
        }

        try {
            String fullPhone = formatPhoneNumber(storePhone);
            String orderNumber = deliveryOrder.getOrderNumber();

            // Build body parameters
            List<Map<String, String>> bodyParams = new ArrayList<>();
            for (String val : Arrays.asList(
                    orderNumber,
                    formatItems(order),
                    "Rs " + deliveryOrder.getOrderAmount().toString(),
                    deliveryOrder.getCustomerName())) {
                Map<String, String> p = new HashMap<>();
                p.put("type", "text");
                p.put("text", val);
                bodyParams.add(p);
            }

            Map<String, Object> bodyComponent = new HashMap<>();
            bodyComponent.put("type", "body");
            bodyComponent.put("parameters", bodyParams);

            // Quick Reply button 0 — Accept (payload encodes order number)
            Map<String, Object> acceptBtn = buildQuickReplyButton(0, "ACCEPT_" + orderNumber);
            // Quick Reply button 1 — Reject
            Map<String, Object> rejectBtn = buildQuickReplyButton(1, "REJECT_" + orderNumber);

            List<Map<String, Object>> components = Arrays.asList(bodyComponent, acceptBtn, rejectBtn);

            Map<String, Object> payload = buildTemplatePayloadWithComponents(fullPhone, "order_notification_v1",
                    components);
            sendWhatsAppRequest(payload);

            deliveryOrder.setNotificationSentAt(LocalDateTime.now());
            deliveryOrder.setStatus(DeliveryOrder.OrderStatus.STORE_NOTIFIED);
            System.out.println("✅ WhatsApp notification sent to store for order: " + orderNumber);
        } catch (Exception e) {
            System.err.println("❌ Failed to send WhatsApp notification: " + e.getMessage());
        }
    }

    /** Builds a Quick Reply button component for a template. */
    private Map<String, Object> buildQuickReplyButton(int index, String payload) {
        Map<String, String> payloadParam = new HashMap<>();
        payloadParam.put("type", "payload");
        payloadParam.put("payload", payload);

        Map<String, Object> btn = new HashMap<>();
        btn.put("type", "button");
        btn.put("sub_type", "quick_reply");
        btn.put("index", String.valueOf(index));
        btn.put("parameters", Collections.singletonList(payloadParam));
        return btn;
    }

    /** Sends a template using a pre-built components list (supports buttons). */
    private Map<String, Object> buildTemplatePayloadWithComponents(String to, String templateName,
            List<Map<String, Object>> components) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "template");

        Map<String, Object> template = new HashMap<>();
        template.put("name", templateName);
        Map<String, String> language = new HashMap<>();
        language.put("code", templateLanguageCode);
        template.put("language", language);
        template.put("components", components);
        payload.put("template", template);
        return payload;
    }

    /**
     * Sends the pickup OTP to the store via WhatsApp.
     */
    public void sendPickupOtpToStore(DeliveryOrder deliveryOrder) {
        sendPickupOtpToStore(deliveryOrder, deliveryOrder.getStore());
    }

    public void sendPickupOtpToStore(DeliveryOrder deliveryOrder, Store targetStore) {
        if (targetStore == null || targetStore.getPhoneNumber() == null || targetStore.getPhoneNumber().isBlank()) {
            throw new IllegalStateException(
                    "Target store phone number is missing for order " + deliveryOrder.getOrderNumber());
        }

        String storePhone = targetStore.getPhoneNumber();
        String fullPhone = formatPhoneNumber(storePhone);
        String otp = deliveryOrder.getPickupOtp();

        System.out.println("[OTP] Sending pickup OTP=" + otp + " to phone=" + fullPhone
                + " for order=" + deliveryOrder.getOrderNumber()
                + " store=" + targetStore.getName());

        // pickup_otp_notification_v2 body is static text (0 body variables).
        // The OTP lives only in the URL button's {{1}} suffix — send only that
        // component.
        Map<String, String> urlParam = new HashMap<>();
        urlParam.put("type", "text");
        urlParam.put("text", otp);

        Map<String, Object> urlButtonComponent = new HashMap<>();
        urlButtonComponent.put("type", "button");
        urlButtonComponent.put("sub_type", "url");
        urlButtonComponent.put("index", "0");
        urlButtonComponent.put("parameters", Collections.singletonList(urlParam));

        Map<String, Object> payload = buildTemplatePayloadWithComponents(
                fullPhone,
                "pickup_otp_notification_v2",
                Collections.singletonList(urlButtonComponent));

        // Let exception propagate — caller will log / return error to admin
        sendWhatsAppRequest(payload);
        deliveryOrder.setPickupOtpSentAt(LocalDateTime.now());
        System.out.println("✅ Pickup OTP sent to store via WhatsApp for order: " + deliveryOrder.getOrderNumber());
    }

    public String sendTemplateMessage(String phoneNumber, String templateName, List<String> bodyParameters) {
        return sendTemplateMessage(phoneNumber, templateName, bodyParameters, null);
    }

    public String sendTemplateMessage(
            String phoneNumber,
            String templateName,
            List<String> bodyParameters,
            String headerMediaUrl) {
        String formattedPhone = formatPhoneNumber(phoneNumber);
        List<Map<String, Object>> components = new ArrayList<>();

        if (headerMediaUrl != null && !headerMediaUrl.isBlank()) {
            String mediaUrl = headerMediaUrl.trim();
            String mediaType = resolveHeaderMediaType(mediaUrl);

            Map<String, Object> media = new HashMap<>();
            media.put("link", mediaUrl);

            Map<String, Object> mediaParam = new HashMap<>();
            mediaParam.put("type", mediaType);
            mediaParam.put(mediaType, media);

            Map<String, Object> headerComponent = new HashMap<>();
            headerComponent.put("type", "header");
            headerComponent.put("parameters", Collections.singletonList(mediaParam));
            components.add(headerComponent);
        }

        if (bodyParameters != null && !bodyParameters.isEmpty()) {
            List<Map<String, String>> paramsList = new ArrayList<>();
            for (String param : bodyParameters) {
                Map<String, String> item = new HashMap<>();
                item.put("type", "text");
                item.put("text", param == null ? "" : param);
                paramsList.add(item);
            }

            Map<String, Object> bodyComponent = new HashMap<>();
            bodyComponent.put("type", "body");
            bodyComponent.put("parameters", paramsList);
            components.add(bodyComponent);
        }

        Map<String, Object> payload = buildTemplatePayloadWithComponents(
                formattedPhone,
                templateName,
                components);
        return sendWhatsAppRequest(payload);
    }

    public String sendTemplateMessageWithHeaderMediaId(
            String phoneNumber,
            String templateName,
            List<String> bodyParameters,
            String headerMediaId,
            String headerMediaType) {
        return sendTemplateMessageWithHeaderMediaIdAndUrlButton(
                phoneNumber,
                templateName,
                bodyParameters,
                headerMediaId,
                headerMediaType,
                null);
    }

    public String sendTemplateMessageWithHeaderMediaIdAndUrlButton(
            String phoneNumber,
            String templateName,
            List<String> bodyParameters,
            String headerMediaId,
            String headerMediaType,
            String urlButtonParameter) {
        String formattedPhone = formatPhoneNumber(phoneNumber);
        List<Map<String, Object>> components = new ArrayList<>();

        if (headerMediaId != null && !headerMediaId.isBlank()) {
            String mediaType = headerMediaType == null || headerMediaType.isBlank() ? "video" : headerMediaType;

            Map<String, Object> media = new HashMap<>();
            media.put("id", headerMediaId);

            Map<String, Object> mediaParam = new HashMap<>();
            mediaParam.put("type", mediaType);
            mediaParam.put(mediaType, media);

            Map<String, Object> headerComponent = new HashMap<>();
            headerComponent.put("type", "header");
            headerComponent.put("parameters", Collections.singletonList(mediaParam));
            components.add(headerComponent);
        }

        addBodyParameters(components, bodyParameters);
        addUrlButtonParameter(components, urlButtonParameter, templateName);

        Map<String, Object> payload = buildTemplatePayloadWithComponents(
                formattedPhone,
                templateName,
                components);
        return sendWhatsAppRequest(payload);
    }

    private void addBodyParameters(List<Map<String, Object>> components, List<String> bodyParameters) {
        if (bodyParameters == null || bodyParameters.isEmpty()) {
            return;
        }

        List<Map<String, String>> paramsList = new ArrayList<>();
        for (String param : bodyParameters) {
            Map<String, String> item = new HashMap<>();
            item.put("type", "text");
            item.put("text", param == null ? "" : param);
            paramsList.add(item);
        }

        Map<String, Object> bodyComponent = new HashMap<>();
        bodyComponent.put("type", "body");
        bodyComponent.put("parameters", paramsList);
        components.add(bodyComponent);
    }

    private void addUrlButtonParameter(List<Map<String, Object>> components, String urlButtonParameter,
            String templateName) {
        if (urlButtonParameter == null || urlButtonParameter.isBlank()) {
            return;
        }
        if (!supportsUrlButton(templateName)) {
            return;
        }

        Map<String, String> urlParam = new HashMap<>();
        urlParam.put("type", "text");
        urlParam.put("text", urlButtonParameter.trim());

        Map<String, Object> urlButtonComponent = new HashMap<>();
        urlButtonComponent.put("type", "button");
        urlButtonComponent.put("sub_type", "url");
        urlButtonComponent.put("index", "0");
        urlButtonComponent.put("parameters", Collections.singletonList(urlParam));
        components.add(urlButtonComponent);
    }

    private boolean supportsUrlButton(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            return false;
        }
        return "refer_and_earn_invite".equalsIgnoreCase(templateName)
                || "lucky".equalsIgnoreCase(templateName)
                || "pg_app_video_v1".equalsIgnoreCase(templateName);
    }

    public String uploadMediaToMeta(MultipartFile file) throws java.io.IOException {
        if (apiToken == null || apiToken.isBlank()) {
            throw new RuntimeException("WHATSAPP_API_TOKEN is not configured");
        }
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new RuntimeException("WHATSAPP_PHONE_NUMBER_ID is not configured");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("WhatsApp media file is required");
        }
        if (file.getSize() > WHATSAPP_VIDEO_MAX_BYTES) {
            throw new IllegalArgumentException("WhatsApp video must be 16 MB or smaller. Current size: "
                    + formatFileSize(file.getSize()));
        }

        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getContentType();
        String filename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "whatsapp-media.mp4"
                : file.getOriginalFilename();

        return uploadMediaBytesToMeta(file.getBytes(), filename, contentType);
    }

    public String uploadMediaUrlToMeta(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            throw new IllegalArgumentException("WhatsApp media URL is required");
        }

        try {
            HttpHeaders downloadHeaders = new HttpHeaders();
            downloadHeaders.set(HttpHeaders.USER_AGENT, "AnushaBazaar-WhatsAppMediaUploader/1.0");
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    mediaUrl.trim(),
                    HttpMethod.GET,
                    new HttpEntity<>(downloadHeaders),
                    byte[].class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                    || response.getBody().length == 0) {
                throw new RuntimeException("Could not download media from URL");
            }
            if (response.getBody().length > WHATSAPP_VIDEO_MAX_BYTES) {
                throw new IllegalArgumentException("WhatsApp video must be 16 MB or smaller. S3 file size: "
                        + formatFileSize(response.getBody().length)
                        + ". Compress the MP4, upload it to S3, then paste the new URL.");
            }

            String contentType = resolveContentTypeFromUrl(mediaUrl);
            if (MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)
                    && response.getHeaders().getContentType() != null) {
                contentType = response.getHeaders().getContentType().toString();
            }
            String filename = resolveFilenameFromUrl(mediaUrl);

            return uploadMediaBytesToMeta(response.getBody(), filename, contentType);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to upload S3 media URL to Meta: " + ex.getMessage(), ex);
        }
    }

    private String uploadMediaBytesToMeta(byte[] bytes, String filename, String contentType)
            throws java.io.IOException {
        if (apiToken == null || apiToken.isBlank()) {
            throw new RuntimeException("WHATSAPP_API_TOKEN is not configured");
        }
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new RuntimeException("WHATSAPP_PHONE_NUMBER_ID is not configured");
        }

        String url = String.format("%s/%s/media", apiUrl, phoneNumberId);
        String safeContentType = contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : contentType;
        String safeFilename = filename == null || filename.isBlank() ? "whatsapp-media.mp4" : filename;

        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return safeFilename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(safeContentType));

        body.add("messaging_product", "whatsapp");
        body.add("type", safeContentType);
        body.add("file", new HttpEntity<>(resource, fileHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(apiToken);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        } catch (RestClientResponseException ex) {
            throw new RuntimeException("WhatsApp media upload error: " + ex.getResponseBodyAsString(), ex);
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("WhatsApp media upload error: " + response.getBody());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String mediaId = root.path("id").asText(null);
        if (mediaId == null || mediaId.isBlank()) {
            throw new RuntimeException("WhatsApp media upload did not return a media id");
        }
        return mediaId;
    }

    private String resolveContentTypeFromUrl(String mediaUrl) {
        String normalized = mediaUrl.toLowerCase(Locale.ROOT);
        if (normalized.contains(".mp4")) {
            return "video/mp4";
        }
        if (normalized.contains(".3gp")) {
            return "video/3gpp";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String resolveFilenameFromUrl(String mediaUrl) {
        try {
            URI uri = URI.create(mediaUrl.trim());
            String path = uri.getPath();
            if (path != null && path.contains("/")) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (!name.isBlank()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // Fall through to default.
        }
        return "whatsapp-media.mp4";
    }

    private String formatFileSize(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.ROOT, "%.2f MB", mb);
    }

    public void notifyCustomerOrderConfirmed(Order order) {
        if (order == null || order.getCustomer() == null) {
            return;
        }

        String customerPhone = order.getCustomer().getPhoneNumber();
        if (customerPhone == null || customerPhone.isBlank()) {
            System.err.println("[WHATSAPP] Customer phone missing for order: " + safeOrderNumber(order));
            return;
        }

        try {
            sendTemplateMessage(
                    customerPhone,
                    customerOrderConfirmedTemplate,
                    Arrays.asList(
                            safeText(order.getCustomer().getName(), "Customer"),
                            safeOrderNumber(order),
                            formatItems(order),
                            formatRupees(order.getGrandTotal()),
                            safeText(order.getPaymentMethod(), "COD"),
                            formatDeliveryAddress(order)));
            System.out.println("[WHATSAPP] Customer confirmation sent for order: " + safeOrderNumber(order));
        } catch (Exception e) {
            System.err.println("[WHATSAPP] Failed to send customer confirmation for order "
                    + safeOrderNumber(order) + ": " + e.getMessage());
        }
    }

    private String resolveHeaderMediaType(String mediaUrl) {
        String normalized = mediaUrl.toLowerCase();
        if (normalized.contains(".mp4") || normalized.contains(".3gp")
                || normalized.contains(".mov") || normalized.contains(".webm")) {
            return "video";
        }
        if (normalized.contains(".pdf")) {
            return "document";
        }
        return "image";
    }

    private String sendWhatsAppRequest(Map<String, Object> payload) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new RuntimeException("WHATSAPP_API_TOKEN is not configured");
        }
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new RuntimeException("WHATSAPP_PHONE_NUMBER_ID is not configured");
        }

        String url = String.format("%s/%s/messages", apiUrl, phoneNumberId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("WhatsApp API error: " + response.getBody());
        }
        return response.getBody();
    }

    private Map<String, Object> buildTemplatePayload(String to, String templateName, List<String> parameters) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "template");

        Map<String, Object> template = new HashMap<>();
        template.put("name", templateName);

        Map<String, String> language = new HashMap<>();
        language.put("code", templateLanguageCode);
        template.put("language", language);

        List<Map<String, Object>> components = new ArrayList<>();
        Map<String, Object> bodyComponent = new HashMap<>();
        bodyComponent.put("type", "body");

        List<Map<String, String>> paramsList = new ArrayList<>();
        for (String param : parameters) {
            Map<String, String> p = new HashMap<>();
            p.put("type", "text");
            p.put("text", param);
            paramsList.add(p);
        }
        bodyComponent.put("parameters", paramsList);
        components.add(bodyComponent);

        template.put("components", components);
        payload.put("template", template);

        return payload;
    }

    /**
     * Phase 4 — Send store-specific OTP for multi-store order split.
     * Template: store_sub_order_otp (params: storeName, itemSummary, otp)
     */
    public void sendSubOrderOtpToStore(com.project.Anusha.model.Store store, Order order,
            String itemSummary, String otp) {
        String storePhone = store.getPhoneNumber();
        if (storePhone == null || storePhone.isEmpty())
            return;

        try {
            Map<String, Object> payload = buildTemplatePayload(
                    formatPhoneNumber(storePhone),
                    "store_sub_order_otp",
                    java.util.Arrays.asList(
                            store.getName(),
                            order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString(),
                            itemSummary.length() > 200 ? itemSummary.substring(0, 200) : itemSummary,
                            otp));
            sendWhatsAppRequest(payload);
            System.out.println(
                    "[WHATSAPP] Sub-order OTP sent to store " + store.getName() + " for order " + order.getId());
        } catch (Exception e) {
            System.err.println("[WHATSAPP] Failed to send sub-order OTP: " + e.getMessage());
        }
    }

    /**
     * Fetches all message templates from Meta Business Suite for this WABA.
     *
     * Calls:
     *   GET {apiUrl}/{wabaId}/message_templates
     *        ?fields=name,status,category,language,components&limit=200
     *
     * Returns the parsed JSON node so the controller can forward it directly
     * to the frontend.  Throws RuntimeException on any Meta error.
     */
    public JsonNode fetchMetaTemplates() {
        if (apiToken == null || apiToken.isBlank()) {
            throw new RuntimeException("WHATSAPP_API_TOKEN is not configured on the server.");
        }
        if (wabaId == null || wabaId.isBlank()) {
            throw new RuntimeException(
                    "WHATSAPP_WABA_ID is not configured on the server. " +
                    "Set it to the WhatsApp Business Account ID found in Meta Business Suite → Business Settings → WhatsApp Accounts.");
        }

        String url = String.format(
                "%s/%s/message_templates?fields=name,status,category,language,components&limit=200",
                apiUrl, wabaId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return objectMapper.readTree(response.getBody());
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            throw new RuntimeException("Meta API error fetching templates: " + body, ex);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fetch Meta templates: " + ex.getMessage(), ex);
        }
    }

    private String formatPhoneNumber(String phone) {
        // Meta API requires phone number with country code without + (e.g.,
        // 91xxxxxxxxxx)
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.length() == 10) {
            return "91" + cleaned; // Assume Indian number if 10 digits
        }
        return cleaned;
    }

    private String formatItems(Order order) {
        if (order == null || order.getItems() == null)
            return "No items";
        StringBuilder sb = new StringBuilder();
        order.getItems().forEach(item -> {
            sb.append(item.getProductName()).append(" (").append(item.getQuantity()).append("), ");
        });
        String result = sb.toString();
        result = result.length() > 2 ? result.substring(0, result.length() - 2) : result;
        return truncate(result, 900);
    }

    private String formatRupees(BigDecimal amount) {
        return "Rs " + (amount == null ? BigDecimal.ZERO : amount).stripTrailingZeros().toPlainString();
    }

    private String formatDeliveryAddress(Order order) {
        if (order == null || order.getAddress() == null) {
            return "Saved delivery address";
        }
        com.project.Anusha.model.Address address = order.getAddress();
        String value = String.join(", ",
                java.util.stream.Stream.of(
                        address.getFlatNumber(),
                        address.getAddressLine1(),
                        address.getAddressLine2(),
                        address.getLandmark(),
                        address.getCity(),
                        address.getPostalCode())
                        .filter(part -> part != null && !part.isBlank())
                        .toArray(String[]::new));
        return value.isBlank() ? "Saved delivery address" : truncate(value, 500);
    }

    private String safeOrderNumber(Order order) {
        if (order == null) {
            return "order";
        }
        if (order.getOrderNumber() != null && !order.getOrderNumber().isBlank()) {
            return order.getOrderNumber();
        }
        return order.getId() != null ? order.getId().toString() : "order";
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
