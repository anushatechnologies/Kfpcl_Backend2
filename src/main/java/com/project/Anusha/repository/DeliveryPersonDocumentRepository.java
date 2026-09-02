package com.project.Anusha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.project.Anusha.model.DeliveryPersonDocument;
import com.project.Anusha.model.DeliveryPersonDocument.DocumentStatus;
import com.project.Anusha.model.DeliveryPersonDocument.DocumentType;

@Repository
public interface DeliveryPersonDocumentRepository extends JpaRepository<DeliveryPersonDocument, Long> {

    List<DeliveryPersonDocument> findByDeliveryPersonId(Long deliveryPersonId);

    List<DeliveryPersonDocument> findByDeliveryPersonIdAndDocumentType(Long deliveryPersonId, DocumentType documentType);

    List<DeliveryPersonDocument> findByStatus(DocumentStatus status);

    List<DeliveryPersonDocument> findByDeliveryPersonIdAndStatus(Long deliveryPersonId, DocumentStatus status);

    @Query("SELECT dpd FROM DeliveryPersonDocument dpd WHERE dpd.deliveryPerson.id = :deliveryPersonId AND dpd.documentType = :documentType AND dpd.status IN ('APPROVED', 'PENDING')")
    Optional<DeliveryPersonDocument> findLatestDocumentByType(@Param("deliveryPersonId") Long deliveryPersonId, @Param("documentType") DocumentType documentType);

    @Query("SELECT dpd FROM DeliveryPersonDocument dpd WHERE dpd.status = :status")
    List<DeliveryPersonDocument> findByStatusForAdminReview(@Param("status") DocumentStatus status);

    @Query("SELECT COUNT(dpd) FROM DeliveryPersonDocument dpd WHERE dpd.deliveryPerson.id = :deliveryPersonId AND dpd.status = 'APPROVED'")
    long countApprovedDocumentsByDeliveryPerson(@Param("deliveryPersonId") Long deliveryPersonId);

    @Query("SELECT CASE WHEN COUNT(dpd) = 3 THEN true ELSE false END FROM DeliveryPersonDocument dpd WHERE dpd.deliveryPerson.id = :deliveryPersonId AND dpd.status = 'APPROVED'")
    boolean areAllDocumentsApproved(@Param("deliveryPersonId") Long deliveryPersonId);
}
