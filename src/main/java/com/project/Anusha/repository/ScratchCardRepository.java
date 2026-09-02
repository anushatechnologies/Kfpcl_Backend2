package com.project.Anusha.repository;

import com.project.Anusha.model.ScratchCard;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScratchCardRepository extends JpaRepository<ScratchCard, Long> {

    List<ScratchCard> findByOwnerIdOrderByCreatedAtDesc(Long ownerUserMainId);

    List<ScratchCard> findByOwnerIdAndStatusOrderByCreatedAtDesc(
            Long ownerUserMainId, ScratchCard.Status status);

    boolean existsByOwnerIdAndType(Long ownerUserMainId, ScratchCard.Type type);

    /** Pessimistic lock used in atomic scratch reveal. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScratchCard s WHERE s.id = :id AND s.owner.id = :ownerId")
    Optional<ScratchCard> lockForScratch(@Param("id") Long id, @Param("ownerId") Long ownerId);

    Page<ScratchCard> findAll(Pageable pageable);

    Page<ScratchCard> findByStatus(ScratchCard.Status status, Pageable pageable);

    long countByStatus(ScratchCard.Status status);

    long countByStatusAndCreatedAtAfter(ScratchCard.Status status, LocalDateTime after);

    @Query("SELECT COALESCE(SUM(s.revealedPoints),0) FROM ScratchCard s WHERE s.status = 'SCRATCHED'")
    long totalRevealedPoints();

    @Query("SELECT COALESCE(SUM(s.revealedPoints),0) FROM ScratchCard s " +
           "WHERE s.status = 'SCRATCHED' AND s.scratchedAt > :after")
    long totalRevealedPointsAfter(@Param("after") LocalDateTime after);

    List<ScratchCard> findByStatusAndExpiresAtBefore(ScratchCard.Status status, LocalDateTime before);

    /**
     * Admin search: filter by status / type / owner phone (LIKE).
     * Any of the params can be null to skip that filter.
     */
    @Query("SELECT s FROM ScratchCard s JOIN s.owner o " +
           "WHERE (:status IS NULL OR s.status = :status) " +
           "AND (:type IS NULL OR s.type = :type) " +
           "AND (:phone IS NULL OR o.phoneNumber LIKE CONCAT('%', :phone, '%'))")
    Page<ScratchCard> adminSearch(@Param("status") ScratchCard.Status status,
                                  @Param("type") ScratchCard.Type type,
                                  @Param("phone") String phone,
                                  Pageable pageable);
}
