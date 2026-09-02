package com.project.Anusha.repository;

import com.project.Anusha.model.Variant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VariantRepository extends JpaRepository<Variant, Long> {
    @Query("SELECT v FROM Variant v JOIN FETCH v.product p JOIN FETCH p.store WHERE v.id IN :ids")
    List<Variant> findAllByIdsWithProduct(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Variant v WHERE v.id = :id")
    Optional<Variant> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Variant v
            SET v.stock = v.stock - :quantity
            WHERE v.id = :id
              AND v.stock >= :quantity
            """)
    int decrementStockIfAvailable(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Variant v
            SET v.stock = COALESCE(v.stock, 0) + :quantity
            WHERE v.id = :id
            """)
    int restoreStock(@Param("id") Long id, @Param("quantity") int quantity);
}
