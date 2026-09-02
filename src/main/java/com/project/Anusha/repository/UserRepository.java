package com.project.Anusha.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.Anusha.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByResetToken(String resetToken);
    Optional<User> findByAdminAccessChallengeToken(String adminAccessChallengeToken);
    boolean existsByRole(String role);
    List<User> findByRoleIn(List<String> roles);
    long countByRole(String role);
}
