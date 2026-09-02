package com.project.Anusha.repository;

import com.project.Anusha.model.UserFcmToken;
import com.project.Anusha.model.UserMain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, Long> {
    List<UserFcmToken> findByUser(UserMain user);
    Optional<UserFcmToken> findByUserAndFcmToken(UserMain user, String fcmToken);
    void deleteByFcmToken(String fcmToken);
}
