package com.project.Anusha.service;

import com.project.Anusha.model.Order;
import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.model.Store;
import com.project.Anusha.model.StoreSubOrder;
import com.project.Anusha.model.Address;
import com.project.Anusha.repository.DeliveryOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StoreOrderDispatchService {

    @Value("${whatsapp.api.url}")
    private String apiUrl;

    @Value("${whatsapp.api.token}")
    private String apiToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.template-language-code:en_US}")
    private String templateLanguageCode;

    @Value("${app.orders.operator-whatsapp:919948598350}")
    private String operatorWhatsapp;

    @Value("${app.orders.auto-fulfill-on-paid:true}")
    private boolean autoFulfillOnPaid;

    @Value("${whatsapp.order-notification-template:order_notification_v2}")
    private String orderNotificationTemplate;

    private final RestTemplate restTemplate;
    private final DeliveryOrderRepository deliveryOrderRepository;

    public StoreOrderDispatchService(RestTemplate restTemplate, DeliveryOrderRepository deliveryOrderRepository) {
        this.restTemplate = restTemplate;
        this.deliveryOrderRepository = deliveryOrderRepository;
    }

    /** Notify the operator once for a COD order placed at checkout. */
    public synchronized void notifyOperatorOfOrder(Order order) {
        sendOperatorOrderNotification(order);
    }

    /** Notify the operator once when a genuinely paid order is confirmed. */
    public synchronized void notifyOperatorOfPaidOrder(Order order) {
        if (!autoFulfillOnPaid || order == null || !"PAID".equalsIgnoreCase(order.getPaymentStatus())) return;
        sendOperatorOrderNotification(order);
    }

    private void sendOperatorOrderNotification(Order order) {
        if (order == null) return;
        if (operatorWhatsapp == null || operatorWhatsapp.isBlank()) {
            throw new IllegalStateException("ORDER_OPERATOR_WHATSAPP is not configured");
        }

        var deliveryOrder = order.getOrderNumber() == null ? null
                : deliveryOrderRepository.findByOrderNumber(order.getOrderNumber()).orElse(null);
        if (deliveryOrder != null && deliveryOrder.getNotificationSentAt() != null) return;

        String orderNumber = order.getOrderNumber() != null ? order.getOrderNumber() : String.valueOf(order.getId());
        String items = order.getItems() == null || order.getItems().isEmpty() ? "No items"
                : order.getItems().stream()
                    .map(item -> (item.getProductName() == null ? "Item" : item.getProductName())
                            + " (" + item.getQuantity() + ")")
                    .reduce((left, right) -> left + ", " + right).orElse("No items");
        String customer = order.getCustomer() != null && order.getCustomer().getName() != null
                ? order.getCustomer().getName() : "Customer";

        String paymentLabel = "PAID".equalsIgnoreCase(order.getPaymentStatus())
                ? "" : " (" + (order.getPaymentMethod() == null ? "COD" : order.getPaymentMethod()) + ")";
        Map<String, Object> payload = buildTemplatePayloadWithButtons(
                formatPhoneNumber(operatorWhatsapp), orderNotificationTemplate,
                Arrays.asList(orderNumber, truncate(items, 900), formatRupees(order.getGrandTotal()) + paymentLabel,
                        truncate(formatDeliveryAddress(order), 500), truncate(customer, 200)),
                "ACCEPT_" + orderNumber, "REJECT_" + orderNumber);
        sendWhatsAppRequest(payload);

        if (deliveryOrder != null) {
            deliveryOrder.setStatus(DeliveryOrder.OrderStatus.STORE_NOTIFIED);
            deliveryOrder.setNotificationSentAt(java.time.LocalDateTime.now());
            deliveryOrderRepository.save(deliveryOrder);
        }
    }

    private String formatDeliveryAddress(Order order) {
        Address address = order.getAddress();
        if (address == null) return "Saved delivery address";
        String value = String.join(", ", java.util.stream.Stream.of(address.getFlatNumber(),
                address.getAddressLine1(), address.getAddressLine2(), address.getLandmark(),
                address.getCity(), address.getState(), address.getPostalCode())
                .filter(part -> part != null && !part.isBlank()).toArray(String[]::new));
        return value.isBlank() ? "Saved delivery address" : value;
    }

    private String formatRupees(BigDecimal amount) {
        return "Rs " + (amount == null ? BigDecimal.ZERO : amount).stripTrailingZeros().toPlainString();
    }

    private String truncate(String value, int maxLength) {
        return value != null && value.length() > maxLength
                ? value.substring(0, Math.max(0, maxLength - 3)) + "..." : value;
    }

    public void notifyStoreOfOrderItems(Store store, Order order, String itemSummary,
                                        BigDecimal storeSubtotal, String customerName) {
        String storePhone = store != null ? store.getPhoneNumber() : null;
        if (storePhone == null || storePhone.isBlank()) {
            String orderNumber = order != null && order.getOrderNumber() != null
                    ? order.getOrderNumber()
                    : "unknown";
            System.err.println("[STORE_DISPATCH] Missing store phone for order " + orderNumber);
            return;
        }

        String safeOrderNumber = order != null && order.getOrderNumber() != null
                ? order.getOrderNumber()
                : String.valueOf(order != null ? order.getId() : "");
        String safeSummary = itemSummary == null || itemSummary.isBlank()
                ? "No items"
                : itemSummary.length() > 200 ? itemSummary.substring(0, 200) : itemSummary;
        String safeCustomer = customerName == null || customerName.isBlank()
                ? "Customer"
                : customerName;
        BigDecimal safeSubtotal = storeSubtotal != null ? storeSubtotal : BigDecimal.ZERO;

        try {
            Map<String, Object> payload = buildTemplatePayloadWithButtons(
                    formatPhoneNumber(storePhone),
                    "order_notification_v1",
                    Arrays.asList(safeOrderNumber, safeSummary, "Rs " + safeSubtotal, safeCustomer),
                    "ACCEPT_" + safeOrderNumber,
                    "REJECT_" + safeOrderNumber
            );
            sendWhatsAppRequest(payload);
        } catch (Exception e) {
            System.err.println("[STORE_DISPATCH] WhatsApp notification failed for order " + safeOrderNumber + ": " + e.getMessage());
        }
    }

    public void notifyStoreOfSubOrder(StoreSubOrder subOrder, String customerName) {
        if (subOrder == null || subOrder.getOrder() == null || subOrder.getStore() == null) {
            throw new IllegalArgumentException("Sub-order, order, and store are required");
        }

        Store store = subOrder.getStore();
        Order order = subOrder.getOrder();
        String storePhone = store.getPhoneNumber();
        if (storePhone == null || storePhone.isBlank()) {
            System.err.println("[STORE_DISPATCH] Missing store phone for sub-order " + subOrder.getId());
            return;
        }

        String safeOrderNumber = order.getOrderNumber() != null
                ? order.getOrderNumber()
                : String.valueOf(order.getId());
        String safeSummary = subOrder.getItemSummary() == null || subOrder.getItemSummary().isBlank()
                ? "No items"
                : subOrder.getItemSummary().length() > 200
                    ? subOrder.getItemSummary().substring(0, 200)
                    : subOrder.getItemSummary();
        String safeCustomer = customerName == null || customerName.isBlank()
                ? "Customer"
                : customerName;
        BigDecimal safeSubtotal = subOrder.getSubtotal() != null ? subOrder.getSubtotal() : BigDecimal.ZERO;

        try {
            Map<String, Object> payload = buildTemplatePayloadWithButtons(
                    formatPhoneNumber(storePhone),
                    "order_notification_v1",
                    Arrays.asList(safeOrderNumber, safeSummary, "Rs " + safeSubtotal, safeCustomer),
                    "ACCEPT_SUBORDER_" + subOrder.getId(),
                    "REJECT_SUBORDER_" + subOrder.getId()
            );
            sendWhatsAppRequest(payload);
        } catch (Exception e) {
            System.err.println("[STORE_DISPATCH] WhatsApp notification failed for sub-order "
                    + subOrder.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Builds template payload with body params + Accept/Reject quick-reply buttons.
     * Required because order_notification_v1 has interactive buttons.
     */
    private Map<String, Object> buildTemplatePayloadWithButtons(String to, String templateName,
                                                                 List<String> bodyParams,
                                                                 String acceptPayload,
                                                                 String rejectPayload) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "template");

        // Body component
        List<Map<String, String>> paramsList = new ArrayList<>();
        for (String param : bodyParams) {
            Map<String, String> p = new HashMap<>();
            p.put("type", "text");
            p.put("text", param);
            paramsList.add(p);
        }
        Map<String, Object> bodyComponent = new HashMap<>();
        bodyComponent.put("type", "body");
        bodyComponent.put("parameters", paramsList);

        // Quick Reply button 0 — Accept
        Map<String, String> acceptParam = new HashMap<>();
        acceptParam.put("type", "payload");
        acceptParam.put("payload", acceptPayload);
        Map<String, Object> acceptBtn = new HashMap<>();
        acceptBtn.put("type", "button");
        acceptBtn.put("sub_type", "quick_reply");
        acceptBtn.put("index", "0");
        acceptBtn.put("parameters", Arrays.asList(acceptParam));

        // Quick Reply button 1 — Reject
        Map<String, String> rejectParam = new HashMap<>();
        rejectParam.put("type", "payload");
        rejectParam.put("payload", rejectPayload);
        Map<String, Object> rejectBtn = new HashMap<>();
        rejectBtn.put("type", "button");
        rejectBtn.put("sub_type", "quick_reply");
        rejectBtn.put("index", "1");
        rejectBtn.put("parameters", Arrays.asList(rejectParam));

        Map<String, Object> template = new HashMap<>();
        template.put("name", templateName);
        Map<String, String> language = new HashMap<>();
        language.put("code", templateLanguageCode);
        template.put("language", language);
        template.put("components", Arrays.asList(bodyComponent, acceptBtn, rejectBtn));
        payload.put("template", template);
        return payload;
    }

    public void sendWhatsAppRequest(Map<String, Object> payload) {
        String url = String.format("%s/%s/messages", apiUrl, phoneNumberId);
        System.out.println("[WA] POST " + url);
        System.out.println("[WA] to=" + payload.get("to") + " phoneNumberId=" + phoneNumberId
                + " tokenSet=" + (apiToken != null && !apiToken.isBlank()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken != null ? apiToken : "");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            System.out.println("[WA] Response " + response.getStatusCode() + ": " + response.getBody());
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("WhatsApp API error " + response.getStatusCode() + ": " + response.getBody());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("[WA] HTTP Error " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
            throw new RuntimeException("WhatsApp API error: " + e.getResponseBodyAsString());
        }
    }

    public String getPhoneNumberId() { return phoneNumberId; }
    public boolean isTokenConfigured() { return apiToken != null && !apiToken.isBlank(); }

    private String formatPhoneNumber(String phone) {
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.length() == 10) {
            return "91" + cleaned;
        }
        return cleaned;
    }
}
