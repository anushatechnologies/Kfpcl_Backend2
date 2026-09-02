package com.project.Anusha.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.model.DeliveryOrder.OrderStatus;

@Repository
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    Optional<DeliveryOrder> findByOrderNumber(String orderNumber);

    List<DeliveryOrder> findByDeliveryPersonId(Long deliveryPersonId);

    List<DeliveryOrder> findByDeliveryPersonIdAndStatus(Long deliveryPersonId, OrderStatus status);

    List<DeliveryOrder> findByStatus(OrderStatus status);

    List<DeliveryOrder> findByStatusIn(List<OrderStatus> statuses);

    @Query("SELECT do FROM DeliveryOrder do WHERE do.deliveryPerson.id = :deliveryPersonId ORDER BY do.createdAt DESC")
    List<DeliveryOrder> findByDeliveryPersonIdOrderByCreatedAtDesc(@Param("deliveryPersonId") Long deliveryPersonId);

    @Query("SELECT do FROM DeliveryOrder do WHERE do.deliveryPerson.id = :deliveryPersonId AND do.status = 'DELIVERED' ORDER BY do.deliveredAt DESC")
    List<DeliveryOrder> findCompletedOrdersByDeliveryPerson(@Param("deliveryPersonId") Long deliveryPersonId);

    @Query("SELECT COUNT(do) FROM DeliveryOrder do WHERE do.deliveryPerson.id = :deliveryPersonId AND do.status = 'DELIVERED' AND do.deliveredAt >= :startDate")
    long countWeeklyCompletedOrders(@Param("deliveryPersonId") Long deliveryPersonId, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT do FROM DeliveryOrder do WHERE do.status IN ('PENDING', 'PLACED') ORDER BY do.createdAt ASC")
    List<DeliveryOrder> findPendingOrdersForAssignment();

    @Query("SELECT do FROM DeliveryOrder do WHERE do.status = 'RIDER_ASSIGNED' AND do.assignedAt < :threshold")
    List<DeliveryOrder> findStaleAssignedOrders(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT COUNT(do) FROM DeliveryOrder do WHERE do.status = :status")
    long countOrdersByStatus(@Param("status") OrderStatus status);

    @Query("SELECT do FROM DeliveryOrder do WHERE do.pickupOtp = :otp AND do.status = 'RIDER_ASSIGNED'")
    Optional<DeliveryOrder> findByPickupOtp(@Param("otp") String otp);

    @Query("SELECT do FROM DeliveryOrder do WHERE do.deliveryOtp = :otp AND do.status = 'PICKED_UP'")
    Optional<DeliveryOrder> findByDeliveryOtp(@Param("otp") String otp);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE DeliveryOrder do SET do.deliveryPerson.id = :riderId, do.status = 'RIDER_ASSIGNED', do.assignedAt = :now, do.acceptedAt = :now WHERE do.id = :orderId AND do.deliveryPerson.id IS NULL AND do.status = 'BROADCASTED_TO_RIDERS'")
    int updateRiderAssignment(@Param("orderId") Long orderId, @Param("riderId") Long riderId, @Param("now") LocalDateTime now);

    /** All orders with a customer rating for a given rider (used for computing average) */
    List<DeliveryOrder> findByDeliveryPersonIdAndCustomerRatingIsNotNull(Long deliveryPersonId);
}
