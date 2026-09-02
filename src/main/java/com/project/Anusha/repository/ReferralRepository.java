package com.project.Anusha.repository;

import com.project.Anusha.model.Referral;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, Long> {

    Optional<Referral> findByRefereeId(Long refereeUserMainId);

    Page<Referral> findByStatus(Referral.Status status, Pageable pageable);

    Page<Referral> findByReferrerId(Long referrerUserMainId, Pageable pageable);

    long countByReferrerIdAndCreatedAtAfter(Long referrerUserMainId, LocalDateTime after);

    long countByReferrerIdAndStatusAndCreatedAtAfter(
            Long referrerUserMainId, Referral.Status status, LocalDateTime after);

    long countBySignupDeviceIdAndCreatedAtAfter(String deviceId, LocalDateTime after);

    long countBySignupIpAndCreatedAtAfter(String ip, LocalDateTime after);

    boolean existsByRefereeId(Long refereeUserMainId);

    @Query("SELECT r FROM Referral r " +
           "WHERE (:status IS NULL OR r.status = :status) " +
           "ORDER BY r.createdAt DESC")
    Page<Referral> searchAll(@Param("status") Referral.Status status, Pageable pageable);

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.signupDeviceId = :deviceId AND r.status = 'REWARDED'")
    long countRewardedByDevice(@Param("deviceId") String deviceId);

    List<Referral> findTop20ByStatusOrderByCreatedAtDesc(Referral.Status status);

    @Query("SELECT r FROM Referral r WHERE r.referrer.id = :referrerId " +
           "AND r.status <> 'BLOCKED' ORDER BY r.createdAt DESC")
    List<Referral> findVisibleByReferrer(@Param("referrerId") Long referrerId);

    @Query("SELECT r FROM Referral r " +
           "WHERE r.status = 'BLOCKED' " +
           "AND r.createdAt >= :after " +
           "AND LOWER(COALESCE(r.fraudReasons, '')) LIKE LOWER(CONCAT('%', :reasonFragment, '%')) " +
           "ORDER BY r.createdAt DESC")
    List<Referral> findBlockedByReasonSince(@Param("after") LocalDateTime after,
                                            @Param("reasonFragment") String reasonFragment);

    @Query("SELECT COUNT(sc) FROM ScratchCard sc WHERE sc.referral.id = :referralId")
    long countCardsForReferral(@Param("referralId") Long referralId);
}
