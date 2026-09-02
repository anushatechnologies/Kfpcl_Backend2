package com.project.Anusha.repository;

import com.project.Anusha.model.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findByUserMainIdOrderByCreatedAtDesc(Long userMainId);

    Optional<AppNotification> findByIdAndUserMainId(Long id, Long userMainId);
}
