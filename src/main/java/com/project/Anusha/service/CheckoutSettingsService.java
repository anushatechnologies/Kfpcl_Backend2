package com.project.Anusha.service;

import com.project.Anusha.dto.CheckoutSettingsResponse;
import com.project.Anusha.dto.CheckoutSettingsUpdateRequest;
import com.project.Anusha.model.CheckoutSettings;
import com.project.Anusha.repository.CheckoutSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional
public class CheckoutSettingsService {

    private static final BigDecimal DEFAULT_DELIVERY_CHARGE = new BigDecimal("25.00");
    private static final BigDecimal DEFAULT_PLATFORM_FEE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_HANDLING_CHARGE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_SMALL_CART_FEE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_SMALL_CART_THRESHOLD = new BigDecimal("100.00");

    private final CheckoutSettingsRepository checkoutSettingsRepository;

    public CheckoutSettings getCurrentSettings() {
        return checkoutSettingsRepository.findTopByOrderByIdDesc()
                .orElseGet(this::createDefaultSettings);
    }

    public CheckoutSettingsResponse getCurrentSettingsResponse() {
        return toResponse(getCurrentSettings());
    }

    public CheckoutSettingsResponse updateSettings(CheckoutSettingsUpdateRequest request) {
        CheckoutSettings settings = getCurrentSettings();

        if (request.getDeliveryCharge() != null) {
            settings.setDeliveryCharge(normalizeAmount(request.getDeliveryCharge(), "deliveryCharge"));
        }
        if (request.getPlatformFee() != null) {
            settings.setPlatformFee(normalizeAmount(request.getPlatformFee(), "platformFee"));
        }
        if (request.getSmallCartFee() != null) {
            settings.setSmallCartFee(normalizeAmount(request.getSmallCartFee(), "smallCartFee"));
        }
        if (request.getSmallCartThreshold() != null) {
            settings.setSmallCartThreshold(normalizeAmount(request.getSmallCartThreshold(), "smallCartThreshold"));
        }
        if (request.getOnlinePaymentEnabled() != null) {
            settings.setOnlinePaymentEnabled(request.getOnlinePaymentEnabled());
        }
        if (request.getCashOnDeliveryEnabled() != null) {
            settings.setCashOnDeliveryEnabled(request.getCashOnDeliveryEnabled());
        }

        if (!Boolean.TRUE.equals(settings.getOnlinePaymentEnabled())
                && !Boolean.TRUE.equals(settings.getCashOnDeliveryEnabled())) {
            throw new IllegalArgumentException("At least one payment method must remain enabled");
        }

        CheckoutSettings saved = checkoutSettingsRepository.save(settings);
        return toResponse(saved);
    }

    public BigDecimal getDeliveryCharge() {
        return normalizePersistedAmount(getCurrentSettings().getDeliveryCharge(), DEFAULT_DELIVERY_CHARGE);
    }

    public BigDecimal getPlatformFee() {
        return normalizePersistedAmount(getCurrentSettings().getPlatformFee(), DEFAULT_PLATFORM_FEE);
    }

    public BigDecimal getHandlingCharge() {
        return DEFAULT_HANDLING_CHARGE;
    }

    public BigDecimal getSmallCartFee() {
        return normalizePersistedAmount(getCurrentSettings().getSmallCartFee(), DEFAULT_SMALL_CART_FEE);
    }

    public BigDecimal getSmallCartThreshold() {
        return normalizePersistedAmount(getCurrentSettings().getSmallCartThreshold(), DEFAULT_SMALL_CART_THRESHOLD);
    }

    public BigDecimal calculateDeliveryCharge(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(getSmallCartThreshold()) >= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return getDeliveryCharge();
    }

    public BigDecimal calculateHandlingCharge(BigDecimal subtotal) {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSmallCartFee(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(getSmallCartThreshold()) >= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return getSmallCartFee();
    }

    public boolean isOnlinePaymentEnabled() {
        return Boolean.TRUE.equals(getCurrentSettings().getOnlinePaymentEnabled());
    }

    public boolean isCashOnDeliveryEnabled() {
        return Boolean.TRUE.equals(getCurrentSettings().getCashOnDeliveryEnabled());
    }

    /** Fetches settings exactly once and returns all values needed for placeOrder in one shot. */
    public OrderChargesContext getOrderChargesContext(BigDecimal subtotal) {
        CheckoutSettings s = checkoutSettingsRepository.findTopByOrderByIdDesc()
                .orElseGet(this::createDefaultSettings);
        BigDecimal threshold = normalizePersistedAmount(s.getSmallCartThreshold(), DEFAULT_SMALL_CART_THRESHOLD);
        boolean underThreshold = subtotal == null || subtotal.compareTo(threshold) < 0;
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new OrderChargesContext(
                Boolean.TRUE.equals(s.getOnlinePaymentEnabled()),
                Boolean.TRUE.equals(s.getCashOnDeliveryEnabled()),
                underThreshold ? normalizePersistedAmount(s.getDeliveryCharge(), DEFAULT_DELIVERY_CHARGE) : zero,
                normalizePersistedAmount(s.getPlatformFee(), DEFAULT_PLATFORM_FEE),
                underThreshold ? normalizePersistedAmount(s.getSmallCartFee(), DEFAULT_SMALL_CART_FEE) : zero
        );
    }

    public record OrderChargesContext(
            boolean onlineEnabled,
            boolean codEnabled,
            BigDecimal deliveryCharge,
            BigDecimal platformFee,
            BigDecimal smallCartFee
    ) {}

    public CheckoutSettingsResponse toResponse(CheckoutSettings settings) {
        return new CheckoutSettingsResponse(
                settings.getId(),
                normalizePersistedAmount(settings.getDeliveryCharge(), DEFAULT_DELIVERY_CHARGE),
                normalizePersistedAmount(settings.getPlatformFee(), DEFAULT_PLATFORM_FEE),
                DEFAULT_HANDLING_CHARGE,
                normalizePersistedAmount(settings.getSmallCartFee(), DEFAULT_SMALL_CART_FEE),
                normalizePersistedAmount(settings.getSmallCartThreshold(), DEFAULT_SMALL_CART_THRESHOLD),
                Boolean.TRUE.equals(settings.getOnlinePaymentEnabled()),
                Boolean.TRUE.equals(settings.getCashOnDeliveryEnabled()),
                settings.getUpdatedAt()
        );
    }

    private CheckoutSettings createDefaultSettings() {
        CheckoutSettings settings = new CheckoutSettings();
        settings.setDeliveryCharge(DEFAULT_DELIVERY_CHARGE);
        settings.setPlatformFee(DEFAULT_PLATFORM_FEE);
        settings.setHandlingCharge(DEFAULT_HANDLING_CHARGE);
        settings.setSmallCartFee(DEFAULT_SMALL_CART_FEE);
        settings.setSmallCartThreshold(DEFAULT_SMALL_CART_THRESHOLD);
        settings.setOnlinePaymentEnabled(Boolean.FALSE);
        settings.setCashOnDeliveryEnabled(Boolean.TRUE);
        return checkoutSettingsRepository.save(settings);
    }

    private BigDecimal normalizeAmount(BigDecimal value, String fieldName) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePersistedAmount(BigDecimal value, BigDecimal defaultValue) {
        return (value == null ? defaultValue : value).setScale(2, RoundingMode.HALF_UP);
    }
}
