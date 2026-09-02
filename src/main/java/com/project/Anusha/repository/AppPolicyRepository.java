package com.project.Anusha.repository;

import com.project.Anusha.model.AppPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppPolicyRepository extends JpaRepository<AppPolicy, Long> {
    Optional<AppPolicy> findByType(String type);
    boolean existsByType(String type);
}
