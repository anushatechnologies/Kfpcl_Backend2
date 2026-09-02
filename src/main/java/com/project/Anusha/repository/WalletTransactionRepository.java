package com.project.Anusha.repository;

import com.project.Anusha.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByUserMainIdOrderByCreatedAtDesc(Long userMainId);
    boolean existsByUserMainIdAndDescription(Long userMainId, String description);
    boolean existsByUserMainIdAndDescriptionContaining(Long userMainId, String descriptionSubstring);
}
