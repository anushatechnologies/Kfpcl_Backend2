package com.project.Anusha.repository;

import com.project.Anusha.model.Cart;
import com.project.Anusha.model.CartItem;
import com.project.Anusha.model.Variant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndVariant(Cart cart, Variant variant);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.variant.id = :variantId")
    Optional<CartItem> findByCartIdAndVariantIdForUpdate(@Param("cartId") Long cartId,
                                                         @Param("variantId") Long variantId);

    @Modifying
    @Transactional
    @Query("DELETE FROM CartItem ci WHERE ci.variant.id = :variantId")
    void deleteByVariantId(@Param("variantId") Long variantId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteByCartId(@Param("cartId") Long cartId);
}
