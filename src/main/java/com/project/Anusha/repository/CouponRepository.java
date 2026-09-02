package com.project.Anusha.repository;

import com.project.Anusha.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeAndIsActiveTrue(String code);
    List<Coupon> findByIsActiveTrueOrderByCreatedAtDesc();
    boolean existsByCode(String code);
}
