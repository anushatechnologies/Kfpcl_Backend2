package com.project.Anusha.repository;

import com.project.Anusha.model.Customer;
import com.project.Anusha.model.OrderItem;
import com.project.Anusha.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.variant v JOIN FETCH v.product p WHERE oi.order.id = :orderId")
    List<OrderItem> findByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT v.product FROM Order o JOIN o.items oi JOIN oi.variant v " +
           "WHERE o.customer = :customer " +
           "GROUP BY v.product " +
           "ORDER BY MAX(o.placedAt) DESC")
    List<Product> findRecentProductsByCustomer(@Param("customer") Customer customer);

    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi " +
           "WHERE oi.order.customer = :customer " +
           "AND oi.variant.product = :product " +
           "AND LOWER(oi.order.orderStatus) = 'delivered'")
    boolean hasDeliveredOrder(@Param("customer") Customer customer, @Param("product") Product product);

    @Query("SELECT oi FROM OrderItem oi " +
           "JOIN FETCH oi.order o " +
           "JOIN FETCH oi.variant v " +
           "JOIN FETCH v.product p " +
           "LEFT JOIN FETCH p.store s " +
           "WHERE LOWER(COALESCE(o.orderStatus, '')) = 'delivered' " +
           "AND COALESCE(o.deliveredAt, o.placedAt) >= :start " +
           "AND COALESCE(o.deliveredAt, o.placedAt) <= :end")
    List<OrderItem> findDeliveredOrderItemsBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Modifying
    @Transactional
    @Query("DELETE FROM OrderItem oi WHERE oi.variant.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
