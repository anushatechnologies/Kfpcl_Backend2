package com.project.Anusha.repository;

import com.project.Anusha.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    long countByCreatedAtAfter(LocalDateTime dateTime);

    /** Find by phone number via UserMain join — FETCH so userMain is initialized even after session clear */
    @Query("SELECT c FROM Customer c JOIN FETCH c.userMain um WHERE um.phoneNumber = :phoneNumber")
    Optional<Customer> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /** Find by Firebase UID via UserMain join — FETCH so userMain is initialized even after session clear */
    @Query("SELECT c FROM Customer c JOIN FETCH c.userMain um WHERE um.fid = :fid")
    Optional<Customer> findByFirebaseUid(@Param("fid") String fid);

    /** Find by UserMain id */
    Optional<Customer> findByUserMainId(Long userMainId);

    // ─── Admin search methods ───────────────────────────────────────────────────

    /** Search by username containing OR phone containing (no active filter) */
    @Query("SELECT c FROM Customer c JOIN c.userMain um " +
           "WHERE LOWER(c.username) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "   OR um.phoneNumber LIKE CONCAT('%', :phoneNumber, '%')")
    Page<Customer> findByNameContainingIgnoreCaseOrPhoneNumberContaining(
            @Param("name") String name,
            @Param("phoneNumber") String phoneNumber,
            Pageable pageable);

    /** Search by username OR phone AND active status */
    @Query("SELECT c FROM Customer c JOIN c.userMain um " +
           "WHERE (LOWER(c.username) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "   OR um.phoneNumber LIKE CONCAT('%', :phoneNumber, '%')) " +
           "   AND c.isActive = :isActive")
    Page<Customer> findByNameContainingIgnoreCaseOrPhoneNumberContainingAndIsActive(
            @Param("name") String name,
            @Param("phoneNumber") String phoneNumber,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    /** Filter by active status only */
    Page<Customer> findByIsActive(Boolean isActive, Pageable pageable);

    /** Lookup by referral code (used to resolve a code to its owner) */
    Optional<Customer> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);

    /**
     * Organic signups in the given window who:
     *   - never used a referral code (no Referral row and no `referred_by_code`)
     *   - never received any ADMIN_GIFT scratch card before
     *   - are still active
     * Returned newest-first so the admin can review what they're about to gift.
     */
    @Query("SELECT c FROM Customer c " +
           "JOIN FETCH c.userMain um " +
           "WHERE c.isActive = true " +
           "AND c.createdAt >= :after " +
           "AND (c.referredByCode IS NULL OR c.referredByCode = '') " +
           "AND NOT EXISTS (SELECT 1 FROM Referral r WHERE r.referee.id = um.id) " +
           "AND NOT EXISTS (SELECT 1 FROM ScratchCard sc " +
           "                WHERE sc.owner.id = um.id AND sc.type = 'ADMIN_GIFT') " +
           "ORDER BY c.createdAt DESC")
    java.util.List<Customer> findUnreferredOrganicSignups(@Param("after") LocalDateTime after);
}
