package com.project.Anusha.repository;

import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Order;
import com.project.Anusha.dto.AdminOrderSearchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerOrderByPlacedAtDesc(Customer customer);
    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByCustomerAndIdempotencyKey(Customer customer, String idempotencyKey);
    Optional<Order> findTopByOrderByIdDesc();

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.address LEFT JOIN FETCH o.customer ORDER BY o.placedAt DESC")
    List<Order> findAllOrderByPlacedAtDesc();

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.address LEFT JOIN FETCH o.customer WHERE o.id = :id")
    Optional<Order> findByIdWithAddressAndCustomer(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.address
            LEFT JOIN FETCH o.customer c
            LEFT JOIN FETCH c.userMain
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.variant v
            LEFT JOIN FETCH v.product p
            LEFT JOIN FETCH p.store
            WHERE o.id = :id
            """)
    Optional<Order> findCustomerOrderDetailsById(@Param("id") Long id);

    @Query("SELECT COUNT(o) FROM Order o WHERE LOWER(o.orderStatus) = LOWER(:status)")
    long countByOrderStatus(@Param("status") String status);

    @Query("SELECT SUM(o.grandTotal) FROM Order o WHERE o.placedAt >= :start AND o.placedAt <= :end AND LOWER(o.orderStatus) != 'cancelled'")
    BigDecimal sumGrandTotalByPlacedAtBetweenAndOrderStatusNot(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Order> findTop10ByOrderByPlacedAtDesc();

    List<Order> findByPlacedAtBetween(LocalDateTime start, LocalDateTime end);

    boolean existsByCustomerAndOrderStatusNot(Customer customer, String status);

    @Query("SELECT COALESCE(SUM(o.grandTotal), 0) FROM Order o " +
            "WHERE UPPER(COALESCE(o.paymentMethod, '')) = UPPER(:paymentMethod) " +
            "AND LOWER(COALESCE(o.orderStatus, '')) = LOWER(:orderStatus)")
    BigDecimal sumGrandTotalByPaymentMethodAndOrderStatus(
            @Param("paymentMethod") String paymentMethod,
            @Param("orderStatus") String orderStatus);

    @Query("SELECT COALESCE(SUM(o.grandTotal), 0) FROM Order o " +
            "WHERE UPPER(COALESCE(o.paymentMethod, '')) = UPPER(:paymentMethod) " +
            "AND LOWER(COALESCE(o.orderStatus, '')) = LOWER(:orderStatus) " +
            "AND COALESCE(o.deliveredAt, o.placedAt) >= :start " +
            "AND COALESCE(o.deliveredAt, o.placedAt) <= :end")
    BigDecimal sumGrandTotalByPaymentMethodAndOrderStatusBetween(
            @Param("paymentMethod") String paymentMethod,
            @Param("orderStatus") String orderStatus,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o " +
            "WHERE UPPER(COALESCE(o.paymentMethod, '')) = UPPER(:paymentMethod) " +
            "AND LOWER(COALESCE(o.orderStatus, '')) = LOWER(:orderStatus) " +
            "ORDER BY COALESCE(o.deliveredAt, o.placedAt) DESC")
    List<Order> findByPaymentMethodAndOrderStatusOrderByEffectiveDateDesc(
            @Param("paymentMethod") String paymentMethod,
            @Param("orderStatus") String orderStatus);

    // ── Period-based order counts ──────────────────────────────────────────

    @Query("SELECT COUNT(o) FROM Order o WHERE o.placedAt >= :start AND o.placedAt <= :end")
    long countByPlacedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.placedAt >= :start AND o.placedAt <= :end AND LOWER(o.orderStatus) = LOWER(:status)")
    long countByPlacedAtBetweenAndStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("status") String status);

    @Query("SELECT COALESCE(SUM(o.grandTotal), 0) FROM Order o WHERE o.placedAt >= :start AND o.placedAt <= :end AND LOWER(o.orderStatus) = 'delivered'")
    BigDecimal sumDeliveredIncomeByPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE LOWER(o.orderStatus) IN ('cancelled', 'rejected')")
    long countCancelledOrders();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.placedAt >= :start AND o.placedAt <= :end AND LOWER(o.orderStatus) IN ('cancelled', 'rejected')")
    long countCancelledByPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.address ORDER BY o.placedAt DESC")
    List<Order> findAllWithCustomerAndAddress();

    /**
     * Admin global search: order number / customer name / customer phone.
     * Uses constructor projection to avoid heavy entity loads.
     */
    @Query("""
        SELECT new com.project.Anusha.dto.AdminOrderSearchResult(
            o.id,
            o.orderNumber,
            COALESCE(c.username, ''),
            COALESCE(um.phoneNumber, ''),
            o.grandTotal,
            o.orderStatus,
            o.placedAt
        )
        FROM Order o
        LEFT JOIN o.customer c
        LEFT JOIN c.userMain um
        WHERE
              LOWER(COALESCE(o.orderNumber, '')) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(COALESCE(c.username, '')) LIKE LOWER(CONCAT('%', :q, '%'))
           OR COALESCE(um.phoneNumber, '') LIKE CONCAT('%', :qDigitsOrRaw, '%')
        ORDER BY o.placedAt DESC
        """)
    List<AdminOrderSearchResult> adminGlobalSearch(
            @Param("q") String q,
            @Param("qDigitsOrRaw") String qDigitsOrRaw,
            Pageable pageable);

    default List<AdminOrderSearchResult> adminGlobalSearch(String q, Pageable pageable) {
        String digits = q == null ? "" : q.replaceAll("[^0-9]", "");
        String phoneQuery = digits.isBlank() ? q : digits;
        return adminGlobalSearch(q, phoneQuery, pageable);
    }
}
