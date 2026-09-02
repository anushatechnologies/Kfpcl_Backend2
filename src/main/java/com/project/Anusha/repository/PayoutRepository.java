package com.project.Anusha.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.project.Anusha.model.Payout;
import com.project.Anusha.model.Payout.PayoutStatus;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {

    List<Payout> findByDeliveryPersonId(Long deliveryPersonId);

    List<Payout> findByDeliveryPersonIdOrderByWeekStartDateDesc(Long deliveryPersonId);

    List<Payout> findByStatus(PayoutStatus status);

    List<Payout> findByStatusOrderByCreatedAtDesc(PayoutStatus status);

    @Query("SELECT p FROM Payout p WHERE p.deliveryPerson.id = :deliveryPersonId AND p.weekStartDate = :weekStartDate")
    Optional<Payout> findByDeliveryPersonAndWeek(@Param("deliveryPersonId") Long deliveryPersonId, @Param("weekStartDate") LocalDateTime weekStartDate);

    @Query("SELECT p FROM Payout p WHERE p.status = 'PENDING' AND p.weekEndDate <= :currentDate")
    List<Payout> findPendingPayoutsForProcessing(@Param("currentDate") LocalDateTime currentDate);

    @Query("SELECT p FROM Payout p WHERE p.deliveryPerson.id = :deliveryPersonId AND p.weekStartDate >= :startDate ORDER BY p.weekStartDate DESC")
    List<Payout> findRecentPayoutsByDeliveryPerson(@Param("deliveryPersonId") Long deliveryPersonId, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT SUM(p.payoutAmount) FROM Payout p WHERE p.deliveryPerson.id = :deliveryPersonId AND p.status = 'PROCESSED'")
    Optional<Double> getTotalPaidAmountByDeliveryPerson(@Param("deliveryPersonId") Long deliveryPersonId);

    @Query("SELECT COUNT(p) FROM Payout p WHERE p.status = :status")
    long countPayoutsByStatus(@Param("status") PayoutStatus status);
}
