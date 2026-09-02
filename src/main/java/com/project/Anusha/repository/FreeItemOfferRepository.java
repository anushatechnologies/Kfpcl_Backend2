package com.project.Anusha.repository;

import com.project.Anusha.model.FreeItemOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FreeItemOfferRepository extends JpaRepository<FreeItemOffer, Long> {
    List<FreeItemOffer> findAllByOrderByUpdatedAtDesc();
    List<FreeItemOffer> findByActiveTrueOrderByUpdatedAtDesc();
}
