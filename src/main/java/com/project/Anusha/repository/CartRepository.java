package com.project.Anusha.repository;

import com.project.Anusha.model.Cart;
import com.project.Anusha.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByCustomer(Customer customer);

    @Query("""
            SELECT DISTINCT c FROM Cart c
            LEFT JOIN FETCH c.customer cu
            LEFT JOIN FETCH cu.userMain
            LEFT JOIN FETCH c.items i
            LEFT JOIN FETCH i.variant v
            LEFT JOIN FETCH v.product p
            LEFT JOIN FETCH p.store
            WHERE c.customer = :customer
            """)
    Optional<Cart> findByCustomerWithItems(@Param("customer") Customer customer);

    @Query("""
            SELECT DISTINCT c FROM Cart c
            LEFT JOIN FETCH c.customer cu
            LEFT JOIN FETCH cu.userMain
            LEFT JOIN FETCH c.items i
            LEFT JOIN FETCH i.variant v
            LEFT JOIN FETCH v.product p
            LEFT JOIN FETCH p.store
            WHERE c.customer = :customer
            """)
    Optional<Cart> findByCustomerWithItemsForCheckout(@Param("customer") Customer customer);
}
