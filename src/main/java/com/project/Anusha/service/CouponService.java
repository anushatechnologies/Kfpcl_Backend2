package com.project.Anusha.service;

import com.project.Anusha.model.Coupon;
import com.project.Anusha.model.CouponUsage;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Order;
import com.project.Anusha.repository.CouponRepository;
import com.project.Anusha.repository.CouponUsageRepository;
import com.project.Anusha.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CouponService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final OrderRepository orderRepository;

    public CouponService(CouponRepository couponRepository,
                         CouponUsageRepository couponUsageRepository,
                         OrderRepository orderRepository) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
        this.orderRepository = orderRepository;
    }

    // ------------------------------------------------------------------ Admin CRUD

    public Coupon createCoupon(Coupon coupon) {
        String normalizedCode = normalizeCouponCode(coupon.getCode());
        if (couponRepository.existsByCode(normalizedCode)) {
            throw new IllegalArgumentException("Coupon code already exists: " + normalizedCode);
        }
        coupon.setCode(normalizedCode);
        validateCouponDates(coupon.getStartDate(), coupon.getExpiryDate());
        return couponRepository.save(coupon);
    }

    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    public List<Coupon> getActiveCoupons() {
        LocalDateTime now = currentBusinessTime();
        return couponRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .filter(coupon -> coupon.getStartDate() == null || !now.isBefore(coupon.getStartDate()))
                .filter(coupon -> coupon.getExpiryDate() == null || !now.isAfter(coupon.getExpiryDate()))
                .toList();
    }

    public Optional<Coupon> getCouponById(Long id) {
        return couponRepository.findById(id);
    }

    public Coupon updateCoupon(Long id, Coupon couponDetails) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));

        String normalizedCode = normalizeCouponCode(couponDetails.getCode());
        if (!coupon.getCode().equalsIgnoreCase(normalizedCode) && couponRepository.existsByCode(normalizedCode)) {
            throw new IllegalArgumentException("Coupon code already exists: " + normalizedCode);
        }
        validateCouponDates(couponDetails.getStartDate(), couponDetails.getExpiryDate());

        coupon.setCode(normalizedCode);
        coupon.setDescription(couponDetails.getDescription());
        coupon.setDiscountType(couponDetails.getDiscountType());
        coupon.setDiscountValue(couponDetails.getDiscountValue());
        coupon.setMinCartValue(couponDetails.getMinCartValue());
        coupon.setMaxDiscountAmount(couponDetails.getMaxDiscountAmount());
        coupon.setStartDate(couponDetails.getStartDate());
        coupon.setExpiryDate(couponDetails.getExpiryDate());
        coupon.setUsageLimit(couponDetails.getUsageLimit());
        coupon.setUsageLimitPerUser(couponDetails.getUsageLimitPerUser());
        coupon.setFirstTimeUserOnly(couponDetails.isFirstTimeUserOnly());
        coupon.setActive(couponDetails.isActive());

        return couponRepository.save(coupon);
    }

    public void deleteCoupon(Long id) {
        couponRepository.deleteById(id);
    }

    // ------------------------------------------------------------------ Customer Logic

    /**
     * Validates a coupon code for a specific customer and cart value.
     * Returns the calculated discount if valid, otherwise throws exception.
     */
    public BigDecimal validateAndCalculateDiscount(String code, Customer customer, BigDecimal cartValue) {
        Coupon coupon = couponRepository.findByCodeAndIsActiveTrue(normalizeCouponCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or inactive coupon code"));

        LocalDateTime now = currentBusinessTime();

        // 1. Check date range
        if (coupon.getStartDate() != null && now.isBefore(coupon.getStartDate())) {
            throw new IllegalArgumentException("Coupon is not yet active");
        }
        if (coupon.getExpiryDate() != null && now.isAfter(coupon.getExpiryDate())) {
            throw new IllegalArgumentException("Coupon has expired");
        }

        // 2. Check minimum cart value
        if (cartValue.compareTo(coupon.getMinCartValue()) < 0) {
            throw new IllegalArgumentException("Minimum cart value of " + coupon.getMinCartValue() + " required");
        }

        // 3. Check total usage limit
        if (coupon.getUsageLimit() != null) {
            long totalUsed = couponUsageRepository.countByCoupon(coupon);
            if (totalUsed >= coupon.getUsageLimit()) {
                throw new IllegalArgumentException("Coupon usage limit reached");
            }
        }

        // 4. Check usage limit per user
        if (coupon.getUsageLimitPerUser() != null) {
            long userUsed = couponUsageRepository.countByCouponAndCustomer(coupon, customer);
            if (userUsed >= coupon.getUsageLimitPerUser()) {
                throw new IllegalArgumentException("You have already used this coupon " + userUsed + " times");
            }
        }

        // 5. Check first-time user only
        if (coupon.isFirstTimeUserOnly()) {
            boolean hasPreviousOrders = orderRepository.existsByCustomerAndOrderStatusNot(customer, "cancelled");
            if (hasPreviousOrders) {
                throw new IllegalArgumentException("This coupon is only available for your first order");
            }
        }

        // 6. Calculate discount
        BigDecimal discount = BigDecimal.ZERO;
        if (coupon.getDiscountType() == Coupon.DiscountType.FIXED_AMOUNT) {
            discount = coupon.getDiscountValue();
        } else if (coupon.getDiscountType() == Coupon.DiscountType.PERCENTAGE) {
            discount = cartValue.multiply(coupon.getDiscountValue()).divide(new BigDecimal(100));
            if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discount = coupon.getMaxDiscountAmount();
            }
        }

        // Ensure discount doesn't exceed cart value
        if (discount.compareTo(cartValue) > 0) {
            discount = cartValue;
        }

        return discount;
    }

    private LocalDateTime currentBusinessTime() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    private String normalizeCouponCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Coupon code is required");
        }
        return code.trim().toUpperCase();
    }

    private void validateCouponDates(LocalDateTime startDate, LocalDateTime expiryDate) {
        if (startDate != null && expiryDate != null && expiryDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Expiry date must be after start date");
        }
    }

    /**
     * Records coupon usage for a completed order.
     */
    public void recordUsage(Coupon coupon, Customer customer, Order order) {
        CouponUsage usage = new CouponUsage();
        usage.setCoupon(coupon);
        usage.setCustomer(customer);
        usage.setOrder(order);
        couponUsageRepository.save(usage);
    }
}
