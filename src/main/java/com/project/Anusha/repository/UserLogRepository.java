package com.project.Anusha.repository;

import com.project.Anusha.model.UserLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserLogRepository extends JpaRepository<UserLog, Long> {
    List<UserLog> findByUserIdOrderByTimestampDesc(Long userId);
    List<UserLog> findByUserRoleOrderByTimestampDesc(String userRole);
    List<UserLog> findByActionOrderByTimestampDesc(String action);

    @Query("""
        SELECT ul FROM UserLog ul
        WHERE (:role IS NULL OR ul.userRole = :role)
          AND (:q IS NULL OR :q = '' OR LOWER(ul.action) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(ul.details, '')) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:fromTs IS NULL OR ul.timestamp >= :fromTs)
          AND (:toTs IS NULL OR ul.timestamp <= :toTs)
        ORDER BY ul.timestamp DESC
        """)
    Page<UserLog> search(
            @Param("role") String role,
            @Param("q") String q,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs,
            Pageable pageable);

    @Query("""
        SELECT COUNT(DISTINCT ul.userId) FROM UserLog ul
        WHERE ul.userId IS NOT NULL
          AND (:role IS NULL OR ul.userRole = :role)
          AND ul.timestamp >= :since
        """)
    long countDistinctUsersSince(@Param("since") LocalDateTime since, @Param("role") String role);
}
