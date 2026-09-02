package com.project.Anusha.repository;

import com.project.Anusha.model.ReferralConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReferralConfigRepository extends JpaRepository<ReferralConfig, Long> {
}
