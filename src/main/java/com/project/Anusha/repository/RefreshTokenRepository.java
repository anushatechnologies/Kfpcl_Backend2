package com.project.Anusha.repository;

import com.project.Anusha.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByPrincipalTypeAndPrincipalIdAndClientTypeAndRevokedFalse(
            RefreshToken.PrincipalType principalType,
            Long principalId,
            RefreshToken.ClientType clientType
    );

    List<RefreshToken> findByPrincipalTypeAndPrincipalIdAndRevokedFalse(
            RefreshToken.PrincipalType principalType,
            Long principalId
    );
}
