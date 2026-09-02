package com.project.Anusha.repository;

import com.project.Anusha.model.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankRepository extends JpaRepository<Bank, Long> {

    @Query("SELECT b FROM Bank b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY b.name ASC")
    List<Bank> searchByName(@Param("q") String q);

    boolean existsByName(String name);
}
