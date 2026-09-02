package com.project.Anusha.repository;

import com.project.Anusha.model.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {
    List<DeliveryZone> findAllByOrderByUpdatedAtDesc();
}
