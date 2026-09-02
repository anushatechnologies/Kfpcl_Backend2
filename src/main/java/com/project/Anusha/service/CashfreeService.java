package com.project.Anusha.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Anusha.config.CashfreeConfig;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreeService {

    private final CashfreeConfig cashfreeConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Helper to build HTTP Headers with Cashfree credentials and API version.
     */
    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-version", cashfreeConfig.getApiVersion());
        headers.set("x-client-id", cashfreeConfig.getAppId());
        headers.set("x-client-secret", cashfreeConfig.getSecretKey());
        return headers;
    }

    /**
     * Creates an order in Cashfree system.
     * Cashfree orders accept decimal amount in INR (rupees, NOT paise).
     *
     * POST /orders
     */
    public Map<String, Object> createOrder(Order order, String receiptId, BigDecimal overrideAmount) {
        BigDecimal amount = (overrideAmount != null && overrideAmount.compareTo(BigDecimal.ZERO) > 0)
                ? overrideAmount : order.getGrandTotal();

        // Round to 2 decimal places as required by Cashfree
        String formattedAmount = amount.setScale(2, BigDecimal.ROUND_HALF_UP).toString();

        Map<String, Object> request = new HashMap<>();
        request.put("order_id", receiptId);
        request.put("order_amount", formattedAmount);
        request.put("order_currency", "INR");

        Map<String, String> customerDetails = new HashMap<>();
        String customerId = "cust_" + (order.getCustomer() != null && order.getCustomer().getId() != null
                ? order.getCustomer().getId() : UUID.randomUUID().toString().substring(0, 8));
        String customerPhone = order.getCustomer() != null && order.getCustomer().getPhoneNumber() != null
                ? order.getCustomer().getPhoneNumber() : "9999999999";
        // Cashfree customer phone must be 10 digits
        customerPhone = customerPhone.replaceAll("[^0-9]", "");
        if (customerPhone.length() > 10) {
            customerPhone = customerPhone.substring(customerPhone.length() - 10);
        } else if (customerPhone.length() < 10) {
            customerPhone = String.format("%-10s", customerPhone).replace(' ', '0'); // pad with zeroes if short
        }

        String customerEmail = order.getCustomer() != null && order.getCustomer().getEmail() != null
                ? order.getCustomer().getEmail() : "customer@example.com";

        customerDetails.put("customer_id", customerId);
        customerDetails.put("customer_phone", customerPhone);
        customerDetails.put("customer_email", customerEmail);
        request.put("customer_details", customerDetails);

        // Meta parameters for return URL
        Map<String, String> orderMeta = new HashMap<>();
        // Cashfree will redirect customers here after hosted page checkout
        String returnUrl = "https://app.anushatechnologies.com/payment/status?order_id=" + receiptId;
        orderMeta.put("return_url", returnUrl);
        request.put("order_meta", orderMeta);

        // Optional internal tags
        Map<String, String> tags = new HashMap<>();
        tags.put("internalOrderId", order.getId().toString());
        tags.put("orderNumber", order.getOrderNumber());
        request.put("order_tags", tags);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, getHeaders());
        String url = cashfreeConfig.getApiUrl() + "/orders";

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JSONObject json = new JSONObject(response.getBody());
            log.info("Cashfree order created: {} for internal order: {}", json.optString("cf_order_id"), order.getId());
            return json.toMap();
        } catch (RestClientResponseException ex) {
            log.error("Cashfree order creation API error: {} | Request: {}", ex.getResponseBodyAsString(), request);
            throw new RuntimeException("Cashfree payment initiation failed: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Cashfree order creation error: {}", ex.getMessage());
            throw new RuntimeException("Failed to initiate Cashfree order: " + ex.getMessage(), ex);
        }
    }

    /**
     * Creates an order in Cashfree specifically for wallet top-ups.
     * Does not require an internal store Order entity.
     *
     * POST /orders
     */
    public Map<String, Object> createWalletTopupOrder(Customer customer, Long userMainId, BigDecimal amount, String receiptId) {
        String formattedAmount = amount.setScale(2, RoundingMode.HALF_UP).toString();

        Map<String, Object> request = new HashMap<>();
        request.put("order_id", receiptId);
        request.put("order_amount", formattedAmount);
        request.put("order_currency", "INR");

        Map<String, String> customerDetails = new HashMap<>();
        String customerId = "cust_" + (customer != null && customer.getId() != null
                ? customer.getId() : (userMainId != null ? userMainId : UUID.randomUUID().toString().substring(0, 8)));
        String customerPhone = customer != null && customer.getPhoneNumber() != null
                ? customer.getPhoneNumber() : "9999999999";
        customerPhone = customerPhone.replaceAll("[^0-9]", "");
        if (customerPhone.length() > 10) {
            customerPhone = customerPhone.substring(customerPhone.length() - 10);
        } else if (customerPhone.length() < 10) {
            customerPhone = String.format("%-10s", customerPhone).replace(' ', '0');
        }

        String customerEmail = customer != null && customer.getEmail() != null
                ? customer.getEmail() : "customer@example.com";

        customerDetails.put("customer_id", customerId);
        customerDetails.put("customer_phone", customerPhone);
        customerDetails.put("customer_email", customerEmail);
        request.put("customer_details", customerDetails);

        Map<String, String> orderMeta = new HashMap<>();
        orderMeta.put("return_url", "https://app.anushatechnologies.com/wallet/status?order_id=" + receiptId);
        request.put("order_meta", orderMeta);

        Map<String, String> tags = new HashMap<>();
        tags.put("type", "WALLET_TOPUP");
        if (userMainId != null) {
            tags.put("userMainId", String.valueOf(userMainId));
        }
        request.put("order_tags", tags);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, getHeaders());
        String url = cashfreeConfig.getApiUrl() + "/orders";

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JSONObject json = new JSONObject(response.getBody());
            log.info("Cashfree wallet top-up order created: {} for userMainId: {}", json.optString("cf_order_id"), userMainId);
            return json.toMap();
        } catch (RestClientResponseException ex) {
            log.error("Cashfree wallet top-up order API error: {} | Request: {}", ex.getResponseBodyAsString(), request);
            throw new RuntimeException("Cashfree wallet payment initiation failed: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Cashfree wallet top-up order creation error: {}", ex.getMessage());
            throw new RuntimeException("Failed to initiate Cashfree wallet order: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetches order payments from Cashfree and checks if any payment transaction succeeded.
     *
     * GET /orders/{cf_order_id}/payments
     */
    public boolean verifyPayment(String cfOrderId) {
        String url = String.format("%s/orders/%s/payments", cashfreeConfig.getApiUrl(), cfOrderId);
        HttpEntity<?> entity = new HttpEntity<>(getHeaders());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.isArray()) {
                for (JsonNode payment : root) {
                    String status = payment.path("payment_status").asText("");
                    if ("SUCCESS".equalsIgnoreCase(status)) {
                        log.info("Verified successful Cashfree payment: {} for order: {}",
                                payment.path("cf_payment_id").asText(), cfOrderId);
                        return true;
                    }
                }
            }
            log.warn("No successful Cashfree payment found for order: {}", cfOrderId);
            return false;
        } catch (RestClientResponseException ex) {
            log.error("Cashfree verify API error: {}", ex.getResponseBodyAsString());
            return false;
        } catch (Exception ex) {
            log.error("Cashfree verify error: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Initiates a refund on a Cashfree order.
     * Cashfree refunds require decimal amount in INR.
     *
     * POST /orders/{cf_order_id}/refunds
     */
    public Map<String, Object> refundPayment(String cfOrderId, BigDecimal amount, String reason) {
        String url = String.format("%s/orders/%s/refunds", cashfreeConfig.getApiUrl(), cfOrderId);

        // Round to 2 decimal places
        String formattedAmount = amount.setScale(2, BigDecimal.ROUND_HALF_UP).toString();

        Map<String, Object> request = new HashMap<>();
        request.put("refund_amount", formattedAmount);
        request.put("refund_id", "REF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        request.put("refund_note", reason != null && !reason.isBlank() ? reason : "Order refund");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, getHeaders());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JSONObject json = new JSONObject(response.getBody());
            log.info("Cashfree refund created: {} for order: {}", json.optString("refund_id"), cfOrderId);
            return json.toMap();
        } catch (RestClientResponseException ex) {
            log.error("Cashfree refund API error: {}", ex.getResponseBodyAsString());
            throw new RuntimeException("Cashfree refund failed: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Cashfree refund error: {}", ex.getMessage());
            throw new RuntimeException("Failed to process Cashfree refund: " + ex.getMessage(), ex);
        }
    }

    /**
     * Verifies Webhook Signature sent by Cashfree.
     * Signature = HMAC-SHA256(timestamp + body, webhookSecret)
     */
    public boolean verifyWebhookSignature(String rawBody, String timestamp, String signature) {
        String secret = cashfreeConfig.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            // Default to app secret key if specific webhook secret isn't set
            secret = cashfreeConfig.getSecretKey();
        }

        try {
            String payload = timestamp + rawBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(hash);
            return expected.equals(signature);
        } catch (Exception e) {
            log.error("Cashfree webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
