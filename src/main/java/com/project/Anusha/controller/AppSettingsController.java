package com.project.Anusha.controller;

import com.project.Anusha.dto.CheckoutSettingsResponse;
import com.project.Anusha.service.CheckoutSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/settings
 *
 * Returns a merged app-settings payload that the customer app reads alongside
 * /api/checkout-settings. Extra fee fields are mirrored here so the customer
 * app can read one combined app-settings payload.
 */
@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AppSettingsController {

    private final CheckoutSettingsService checkoutSettingsService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAppSettings() {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            CheckoutSettingsResponse checkout = checkoutSettingsService.getCurrentSettingsResponse();

            // Mirror the checkout-settings fields so the frontend can unify both
            result.put("deliveryCharge", checkout.getDeliveryCharge() != null
                    ? checkout.getDeliveryCharge() : BigDecimal.ZERO);
            result.put("platformFee", checkout.getPlatformFee() != null
                    ? checkout.getPlatformFee() : BigDecimal.ZERO);
            result.put("handlingCharge", BigDecimal.ZERO);
            result.put("smallCartFee", checkout.getSmallCartFee() != null
                    ? checkout.getSmallCartFee() : BigDecimal.ZERO);
            result.put("smallCartThreshold", checkout.getSmallCartThreshold() != null
                    ? checkout.getSmallCartThreshold() : BigDecimal.ZERO);
            result.put("onlinePaymentEnabled", checkout.isOnlinePaymentEnabled());
            result.put("cashOnDeliveryEnabled", checkout.isCashOnDeliveryEnabled());
        } catch (Exception ignored) {
            result.put("deliveryCharge", BigDecimal.ZERO);
            result.put("platformFee", BigDecimal.ZERO);
            result.put("handlingCharge", BigDecimal.ZERO);
            result.put("smallCartFee", BigDecimal.ZERO);
            result.put("smallCartThreshold", BigDecimal.ZERO);
            result.put("onlinePaymentEnabled", false);
            result.put("cashOnDeliveryEnabled", true);
        }

        result.put("maxItemsPerOrder", 50);
        result.put("gstPercentage", BigDecimal.ZERO);

        return ResponseEntity.ok(result);
    }
}
