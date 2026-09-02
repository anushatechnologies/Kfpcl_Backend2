package com.project.Anusha.repository;

import com.project.Anusha.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    Optional<PaymentTransaction> findTopByOrderIdAndStatusAndPaymentMethodOrderByCreatedAtDesc(
            Long orderId,
            String status,
            String paymentMethod);

    List<PaymentTransaction> findAllByOrderByCreatedAtDesc();

    @Query("SELECT SUM(p.amount) FROM PaymentTransaction p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") String status);

    @Query("SELECT SUM(p.amount) FROM PaymentTransaction p WHERE p.status = :status AND p.createdAt >= :start AND p.createdAt <= :end")
    BigDecimal sumAmountByStatusAndCreatedAtBetween(@Param("status") String status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
