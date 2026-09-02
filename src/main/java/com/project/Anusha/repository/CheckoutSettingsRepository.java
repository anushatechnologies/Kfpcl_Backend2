package com.project.Anusha.repository;

import com.project.Anusha.model.CheckoutSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CheckoutSettingsRepository extends JpaRepository<CheckoutSettings, Long> {
    Optional<CheckoutSettings> findTopByOrderByIdDesc();
}
