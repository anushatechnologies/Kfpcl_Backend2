package com.project.Anusha.repository;

import com.project.Anusha.model.UserMain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserMainRepository extends JpaRepository<UserMain, Long> {

    long countByCreatedAtAfter(LocalDateTime dateTime);

    /** Lookup by Firebase UID (fid) */
    Optional<UserMain> findByFid(String fid);

    /** Lookup by phone number */
    Optional<UserMain> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByFid(String fid);
}
