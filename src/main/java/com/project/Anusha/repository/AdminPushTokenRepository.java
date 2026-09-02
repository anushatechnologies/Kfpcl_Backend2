package com.project.Anusha.repository;

import com.project.Anusha.model.AdminPushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminPushTokenRepository extends JpaRepository<AdminPushToken, Long> {

    Optional<AdminPushToken> findByExpoPushToken(String expoPushToken);

    @Query("select t from AdminPushToken t " +
           "where t.admin.id = :adminId and t.active = true and t.appType = 'ADMIN_APP'")
    List<AdminPushToken> findActiveByAdminId(@Param("adminId") Long adminId);

    /** All active admin-app tokens — used for the broadcast on new-order events. */
    @Query("select t from AdminPushToken t " +
           "where t.active = true and t.appType = 'ADMIN_APP'")
    List<AdminPushToken> findAllActiveAdminApp();
}
