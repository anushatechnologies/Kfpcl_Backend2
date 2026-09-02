package com.project.Anusha.repository;

import com.project.Anusha.model.StoreSubOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreSubOrderRepository extends JpaRepository<StoreSubOrder, Long> {

    List<StoreSubOrder> findByOrderId(Long orderId);

    List<StoreSubOrder> findByStoreId(Long storeId);

    @Query("SELECT s FROM StoreSubOrder s WHERE s.order.id = :orderId AND s.store.id = :storeId")
    java.util.Optional<StoreSubOrder> findByOrderIdAndStoreId(@Param("orderId") Long orderId, @Param("storeId") Long storeId);

    List<StoreSubOrder> findByStatus(StoreSubOrder.SubOrderStatus status);
}
