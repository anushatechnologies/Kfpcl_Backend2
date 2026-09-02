package com.project.Anusha.repository;

import com.project.Anusha.model.FraudBlocklist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FraudBlocklistRepository extends JpaRepository<FraudBlocklist, Long> {
    Optional<FraudBlocklist> findByEntryTypeAndEntryValue(FraudBlocklist.EntryType type, String value);
    boolean existsByEntryTypeAndEntryValue(FraudBlocklist.EntryType type, String value);
    Page<FraudBlocklist> findByEntryType(FraudBlocklist.EntryType type, Pageable pageable);
}
