package com.project.Anusha.service;

import com.project.Anusha.model.UserMain;
import com.project.Anusha.model.WalletTransaction;
import com.project.Anusha.repository.UserMainRepository;
import com.project.Anusha.repository.WalletTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class WalletService {

    @Autowired
    private UserMainRepository userMainRepository;

    @Autowired
    private WalletTransactionRepository transactionRepository;

    public void addMoney(Long userMainId, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        UserMain user = userMainRepository.findById(userMainId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        BigDecimal current = user.getWalletBalance() != null ? user.getWalletBalance() : BigDecimal.ZERO;
        user.setWalletBalance(current.add(amount));
        userMainRepository.save(user);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserMain(user);
        transaction.setAmount(amount);
        transaction.setType(WalletTransaction.TransactionType.CREDIT);
        transaction.setDescription(description);
        transactionRepository.save(transaction);
    }

    public void deductMoney(Long userMainId, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        UserMain user = userMainRepository.findById(userMainId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        BigDecimal current = user.getWalletBalance() != null ? user.getWalletBalance() : BigDecimal.ZERO;
        if (current.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient wallet balance");
        }

        user.setWalletBalance(current.subtract(amount));
        userMainRepository.save(user);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserMain(user);
        transaction.setAmount(amount);
        transaction.setType(WalletTransaction.TransactionType.DEBIT);
        transaction.setDescription(description);
        transactionRepository.save(transaction);
    }

    public List<WalletTransaction> getTransactionHistory(Long userMainId) {
        return transactionRepository.findByUserMainIdOrderByCreatedAtDesc(userMainId);
    }

    public boolean isTopupAlreadyCredited(Long userMainId, String cfOrderId) {
        if (cfOrderId == null || cfOrderId.isBlank()) {
            return false;
        }
        return transactionRepository.existsByUserMainIdAndDescriptionContaining(userMainId, cfOrderId);
    }

    public BigDecimal getBalance(Long userMainId) {
        UserMain user = userMainRepository.findById(userMainId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getWalletBalance() != null ? user.getWalletBalance() : BigDecimal.ZERO;
    }
}
