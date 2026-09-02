package com.project.Anusha.repository;

import com.project.Anusha.model.Coupon;
import com.project.Anusha.model.CouponUsage;
import com.project.Anusha.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {
    long countByCoupon(Coupon coupon);
    long countByCouponAndCustomer(Coupon coupon, Customer customer);
}
