package com.project.Anusha.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_requests")
@Data
public class ApprovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String approvalType;
    private String targetType;
    private Long targetId;
    private String requestedByEmail;
    private String approvedByEmail;
    private String status = "PENDING";

    @Column(length = 1000)
    private String reason;

    @Column(length = 2000)
    private String payloadJson;

    @Column(length = 1000)
    private String decisionNotes;

    private LocalDateTime decidedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
