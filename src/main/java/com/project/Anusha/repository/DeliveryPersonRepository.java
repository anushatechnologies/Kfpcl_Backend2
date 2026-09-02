package com.project.Anusha.repository;

import com.project.Anusha.model.DeliveryPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryPersonRepository extends JpaRepository<DeliveryPerson, Long> {

    /** Find by phone number via UserMain join */
    @Query("SELECT dp FROM DeliveryPerson dp JOIN dp.userMain um WHERE um.phoneNumber = :phoneNumber")
    Optional<DeliveryPerson> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /** Check existence by phone number */
    @Query("SELECT COUNT(dp) > 0 FROM DeliveryPerson dp JOIN dp.userMain um WHERE um.phoneNumber = :phoneNumber")
    boolean existsByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /** Find by Firebase UID via UserMain join */
    @Query("SELECT dp FROM DeliveryPerson dp JOIN dp.userMain um WHERE um.fid = :fid")
    Optional<DeliveryPerson> findByFirebaseUid(@Param("fid") String fid);

    /** Find by UserMain id */
    Optional<DeliveryPerson> findByUserMainId(Long userMainId);

    List<DeliveryPerson> findByIsOnlineTrue();

    List<DeliveryPerson> findByApprovalStatus(DeliveryPerson.ApprovalStatus status);

    /** Delivery persons that are pending admin review */
    @Query("SELECT dp FROM DeliveryPerson dp WHERE dp.approvalStatus = 'PENDING' AND dp.isVerified = true")
    List<DeliveryPerson> findPendingApproval();

    /** Approved and verified but not yet assigned to an order */
    @Query("SELECT dp FROM DeliveryPerson dp WHERE dp.isOnline = true " +
           "AND dp.isApprovedByAdmin = true " +
           "AND dp.id NOT IN (" +
           "  SELECT do.deliveryPerson.id FROM DeliveryOrder do " +
           "  WHERE do.status IN ('RIDER_ASSIGNED', 'REACHED_STORE', 'PICKED_UP', 'OUT_FOR_DELIVERY'))")
    List<DeliveryPerson> findAvailableDeliveryPersons();

    @Query("SELECT dp FROM DeliveryPerson dp WHERE dp.lastActiveTime < :threshold")
    List<DeliveryPerson> findInactiveDeliveryPersons(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT COUNT(dp) FROM DeliveryPerson dp " +
           "WHERE dp.isOnline = true AND dp.isApprovedByAdmin = true AND dp.isVerified = true")
    long countActiveDeliveryPersons();

    @Query("SELECT dp FROM DeliveryPerson dp WHERE dp.registrationDate >= :startDate")
    List<DeliveryPerson> findDeliveryPersonsRegisteredAfter(@Param("startDate") LocalDateTime startDate);

    /** Approved + verified (admin dashboard) */
    @Query("SELECT dp FROM DeliveryPerson dp WHERE dp.isApprovedByAdmin = true AND dp.isVerified = true")
    List<DeliveryPerson> findByIsApprovedByAdminTrueAndIsVerifiedTrue();

    @Query("SELECT COUNT(dp) FROM DeliveryPerson dp WHERE dp.isApprovedByAdmin = true AND dp.isVerified = true")
    long countApprovedDeliveryPersons();

    @Query("SELECT COUNT(dp) FROM DeliveryPerson dp WHERE dp.isOnline = true")
    long countOnlineDeliveryPersons();

    /** Online, approved, verified (available pool) */
    @Query("SELECT dp FROM DeliveryPerson dp " +
           "WHERE dp.isOnline = true AND dp.isApprovedByAdmin = true AND dp.isVerified = true")
    List<DeliveryPerson> findByIsOnlineTrueAndIsApprovedByAdminTrueAndIsVerifiedTrue();

    /**
     * Haversine formula — find online+approved riders within radiusKm of a point (native MySQL).
     * Returns riders sorted nearest-first.
     */
    @Query(value = """
            SELECT dp.* FROM delivery_persons dp
            WHERE dp.is_online = 1
              AND dp.is_approved_by_admin = 1
              AND dp.is_verified = 1
              AND dp.latitude IS NOT NULL
              AND dp.longitude IS NOT NULL
              AND (6371 * ACOS(
                    COS(RADIANS(:lat)) * COS(RADIANS(dp.latitude)) *
                    COS(RADIANS(dp.longitude) - RADIANS(:lng)) +
                    SIN(RADIANS(:lat)) * SIN(RADIANS(dp.latitude))
                  )) <= :radiusKm
            ORDER BY (6371 * ACOS(
                    COS(RADIANS(:lat)) * COS(RADIANS(dp.latitude)) *
                    COS(RADIANS(dp.longitude) - RADIANS(:lng)) +
                    SIN(RADIANS(:lat)) * SIN(RADIANS(dp.latitude))
                  )) ASC
            """, nativeQuery = true)
    List<DeliveryPerson> findNearbyOnlineRiders(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm);

    /** Update GPS location for a rider */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE DeliveryPerson dp SET dp.latitude = :lat, dp.longitude = :lng, dp.locationUpdatedAt = :updatedAt WHERE dp.id = :id")
    int updateLocation(@Param("id") Long id, @Param("lat") double lat, @Param("lng") double lng, @Param("updatedAt") LocalDateTime updatedAt);
}
