package com.project.Anusha.repository;

import com.project.Anusha.model.SavedFilter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedFilterRepository extends JpaRepository<SavedFilter, Long> {
    List<SavedFilter> findByResourceOrderByCreatedAtDesc(String resource);
}

