package com.project.Anusha.repository;

import com.project.Anusha.model.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    List<ApprovalRequest> findAllByOrderByCreatedAtDesc();
}
