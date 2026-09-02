package com.project.Anusha.repository;

import com.project.Anusha.model.DeliveryBroadcast;
import com.project.Anusha.model.DeliveryPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryBroadcastRepository extends JpaRepository<DeliveryBroadcast, Long> {

    /** Active (non-expired, non-accepted) broadcast for a given order */
    @Query("SELECT b FROM DeliveryBroadcast b WHERE b.order.id = :orderId AND b.status = 'PENDING' AND b.expiresAt > :now")
    Optional<DeliveryBroadcast> findActiveBroadcastForOrder(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    /** All PENDING broadcasts matching a vehicle type that haven't expired yet — used by delivery app */
    @Query("SELECT b FROM DeliveryBroadcast b WHERE b.vehicleType = :vehicleType AND b.status = 'PENDING' AND b.expiresAt > :now ORDER BY b.createdAt DESC")
    List<DeliveryBroadcast> findAvailableByVehicleType(@Param("vehicleType") DeliveryPerson.VehicleType vehicleType, @Param("now") LocalDateTime now);

    /** Find any active broadcast for an order (regardless of vehicle type) */
    @Query("SELECT b FROM DeliveryBroadcast b WHERE b.order.id = :orderId AND b.status = 'PENDING'")
    List<DeliveryBroadcast> findPendingByOrderId(@Param("orderId") Long orderId);

    /** Expire all stale broadcasts */
    @Query("SELECT b FROM DeliveryBroadcast b WHERE b.status = 'PENDING' AND b.expiresAt <= :now")
    List<DeliveryBroadcast> findExpiredBroadcasts(@Param("now") LocalDateTime now);
}
