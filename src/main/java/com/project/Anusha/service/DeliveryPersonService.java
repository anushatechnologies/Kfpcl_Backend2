package com.project.Anusha.service;

import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.DeliveryPersonDocument;
import com.project.Anusha.model.DeliveryPersonDocument.DocumentStatus;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.repository.DeliveryPersonDocumentRepository;
import com.project.Anusha.repository.DeliveryPersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DeliveryPersonService {

    @Autowired
    private DeliveryPersonRepository deliveryPersonRepository;

    @Autowired
    private DeliveryPersonDocumentRepository documentRepository;

    @Autowired
    private DeliveryOnboardingService deliveryOnboardingService;

    // ------------------------------------------------------------------ core

    /**
     * Create a new delivery person linked to the given UserMain.
     * Phone + fid are stored in UserMain – not duplicated here.
     */
    public DeliveryPerson createDeliveryPerson(UserMain userMain, String firstName, String lastName,
            String vehicleType, String vehicleModel, String registrationNumber,
            String profilePhotoUrl) {
        DeliveryPerson person = new DeliveryPerson();
        person.setUserMain(userMain);
        person.setFirstName(firstName.trim());
        person.setLastName(lastName.trim());
        person.setVerified(true); // phone already verified by Firebase
        person.setApprovedByAdmin(false); // pending admin approval
        person.setApprovalStatus(DeliveryPerson.ApprovalStatus.PENDING);

        // Use provided vehicle info if present
        if (vehicleType != null && !vehicleType.isBlank()) {
            person.setVehicleType(DeliveryPerson.VehicleType.valueOf(vehicleType.toUpperCase()));
        } else {
            person.setVehicleType(DeliveryPerson.VehicleType.BIKE);
        }

        person.setVehicleModel(vehicleModel != null && !vehicleModel.isBlank() ? vehicleModel.trim() : "Not Specified");
        person.setRegistrationNumber(
                registrationNumber != null && !registrationNumber.isBlank() ? registrationNumber.trim()
                        : "TEMP-" + System.currentTimeMillis());

        if (profilePhotoUrl != null && !profilePhotoUrl.isBlank()) {
            person.setProfilePhotoUrl(profilePhotoUrl.trim());
            person.setProfilePhotoStatus(DeliveryPersonDocument.DocumentStatus.PENDING);
        }

        return deliveryPersonRepository.save(person);
    }

    // ------------------------------------------------------------------ lookup

    public Optional<DeliveryPerson> findByPhoneNumber(String phoneNumber) {
        return deliveryPersonRepository.findByPhoneNumber(phoneNumber);
    }

    public Optional<DeliveryPerson> findByFirebaseUid(String fid) {
        return deliveryPersonRepository.findByFirebaseUid(fid);
    }

    public Optional<DeliveryPerson> findByUserMain(UserMain userMain) {
        return deliveryPersonRepository.findByUserMainId(userMain.getId());
    }

    public boolean existsByPhoneNumber(String phoneNumber) {
        return deliveryPersonRepository.existsByPhoneNumber(phoneNumber);
    }

    public boolean existsByUserMain(UserMain userMain) {
        return deliveryPersonRepository.findByUserMainId(userMain.getId()).isPresent();
    }

    public DeliveryPerson getDeliveryPersonById(Long id) {
        return deliveryPersonRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));
    }

    public Optional<DeliveryPerson> getDeliveryPersonByPhoneNumber(String phoneNumber) {
        return deliveryPersonRepository.findByPhoneNumber(phoneNumber);
    }

    // ------------------------------------------------------------------ update

    public DeliveryPerson updateDeliveryPerson(DeliveryPerson person) {
        return deliveryPersonRepository.save(person);
    }

    public DeliveryPerson updateVehicleInfo(Long deliveryPersonId, DeliveryPerson.VehicleType vehicleType,
            String vehicleModel, String registrationNumber) {
        DeliveryPerson dp = getDeliveryPersonById(deliveryPersonId);
        dp.setVehicleType(vehicleType);
        dp.setVehicleModel(vehicleModel);
        dp.setRegistrationNumber(registrationNumber);
        return deliveryPersonRepository.save(dp);
    }

    public DeliveryPerson updateOnlineStatus(Long deliveryPersonId, boolean isOnline) {
        DeliveryPerson dp = getDeliveryPersonById(deliveryPersonId);

        // Accumulate session duration when going offline
        if (!isOnline && dp.isOnline() && dp.getLastActiveTime() != null) {
            long sessionMinutes = java.time.Duration.between(dp.getLastActiveTime(), LocalDateTime.now()).toMinutes();
            if (sessionMinutes > 0) {
                dp.setTotalLoginMinutes(dp.getTotalLoginMinutes() + sessionMinutes);
            }
        }

        dp.setOnline(isOnline);
        dp.setLastActiveTime(LocalDateTime.now());
        return deliveryPersonRepository.save(dp);
    }

    public DeliveryPerson updateProfile(Long deliveryPersonId, String firstName, String lastName,
            String profilePhotoUrl) {
        DeliveryPerson dp = getDeliveryPersonById(deliveryPersonId);
        if (firstName != null && !firstName.isBlank()) {
            dp.setFirstName(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            dp.setLastName(lastName.trim());
        }

        // Locked if photo is already approved
        if (profilePhotoUrl != null && !profilePhotoUrl.isBlank()) {
            if (dp.getProfilePhotoStatus() == DeliveryPersonDocument.DocumentStatus.APPROVED) {
                throw new IllegalStateException("Cannot change profile photo after admin approval");
            }
            dp.setProfilePhotoUrl(profilePhotoUrl.trim());
            dp.setProfilePhotoStatus(DeliveryPersonDocument.DocumentStatus.PENDING);
            dp.setProfilePhotoRemarks(null);
        }
        return deliveryPersonRepository.save(dp);
    }

    public DeliveryPerson updateBankDetails(Long deliveryPersonId, String accountName, String accountNumber,
            String bankName, String ifscCode) {
        DeliveryPerson dp = getDeliveryPersonById(deliveryPersonId);
        dp.setAccountName(accountName);
        dp.setAccountNumber(accountNumber);
        dp.setBankName(bankName);
        dp.setIfscCode(ifscCode);
        return deliveryPersonRepository.save(dp);
    }

    public DeliveryPerson approveDeliveryPerson(Long deliveryPersonId) {
        DeliveryPerson dp = getDeliveryPersonById(deliveryPersonId);
        if (!dp.isVerified()) {
            throw new IllegalArgumentException("Delivery person must be phone-verified first");
        }
        if (!deliveryOnboardingService.isReadyForFinalApproval(dp, getDeliveryPersonDocuments(deliveryPersonId))) {
            throw new IllegalArgumentException(
                    "Profile, vehicle, bank details, profile photo, and all required documents must be approved before approving the person");
        }
        dp.setApprovedByAdmin(true);
        dp.setApprovalStatus(DeliveryPerson.ApprovalStatus.APPROVED);
        return deliveryPersonRepository.save(dp);
    }

    // ------------------------------------------------------------------ documents

    public DeliveryPersonDocument uploadDocument(Long deliveryPersonId,
            DeliveryPersonDocument.DocumentType documentType,
            String documentUrl,
            String documentNumber) {
        DeliveryPerson dp = getDeliveryPersonById(deliveryPersonId);

        // Check if a document of this type already exists
        List<DeliveryPersonDocument> existing = documentRepository.findByDeliveryPersonId(deliveryPersonId);
        for (DeliveryPersonDocument existingDoc : existing) {
            if (existingDoc.getDocumentType() == documentType) {
                // LOCKED: once approved, document cannot be replaced
                if (existingDoc.getStatus() == DocumentStatus.APPROVED) {
                    throw new IllegalStateException(
                            existingDoc.getDocumentType().getDisplayName()
                                    + " is already approved and cannot be replaced.");
                }
                // Non-approved: update existing row (REJECTED / NEEDS_REUPLOAD / PENDING)
                existingDoc.setDocumentUrl(documentUrl);
                existingDoc.setDocumentNumber(documentNumber);
                existingDoc.setStatus(DocumentStatus.PENDING);
                existingDoc.setAdminRemarks(null);
                existingDoc.setReviewedAt(null);
                existingDoc.setUploadedAt(java.time.LocalDateTime.now());
                DeliveryPersonDocument updatedDoc = documentRepository.save(existingDoc);

                // SYNC: If this is a profile photo, update the main delivery_person record too
                if (documentType == DeliveryPersonDocument.DocumentType.PROFILE_PHOTO) {
                    dp.setProfilePhotoUrl(documentUrl);
                    dp.setProfilePhotoStatus(DocumentStatus.PENDING);
                    dp.setProfilePhotoRemarks(null);
                    deliveryPersonRepository.save(dp);
                }

                return updatedDoc;
            }
        }

        // First upload for this document type
        DeliveryPersonDocument doc = new DeliveryPersonDocument();
        doc.setDeliveryPerson(dp);
        doc.setDocumentType(documentType);
        doc.setDocumentUrl(documentUrl);
        doc.setDocumentNumber(documentNumber);
        doc.setStatus(DocumentStatus.PENDING);

        DeliveryPersonDocument savedDoc = documentRepository.save(doc);

        // SYNC: If this is a profile photo, update the main delivery_person record too
        if (documentType == DeliveryPersonDocument.DocumentType.PROFILE_PHOTO) {
            dp.setProfilePhotoUrl(documentUrl);
            dp.setProfilePhotoStatus(DocumentStatus.PENDING);
            dp.setProfilePhotoRemarks(null);
            deliveryPersonRepository.save(dp);
        }

        return savedDoc;
    }

    public List<DeliveryPersonDocument> getDeliveryPersonDocuments(Long deliveryPersonId) {
        return documentRepository.findByDeliveryPersonId(deliveryPersonId);
    }

    public boolean areAllDocumentsApproved(Long deliveryPersonId) {
        DeliveryPerson deliveryPerson = getDeliveryPersonById(deliveryPersonId);
        return deliveryOnboardingService.areRequiredDocumentsApproved(
                deliveryPerson,
                getDeliveryPersonDocuments(deliveryPersonId));
    }

    // ------------------------------------------------------------------ lists

    public List<DeliveryPerson> getAvailableDeliveryPersons() {
        return deliveryPersonRepository.findAvailableDeliveryPersons();
    }

    public List<DeliveryPerson> getDeliveryPersonsPendingApproval() {
        return deliveryPersonRepository.findPendingApproval();
    }

    // ------------------------------------------------------------------ earnings

    public void updateEarnings(Long deliveryPersonId, Double deliveryFee) {
        DeliveryPerson dp = getDeliveryPersonById(deliveryPersonId);
        dp.setTotalEarnings(dp.getTotalEarnings().add(BigDecimal.valueOf(deliveryFee)));
        dp.setWeeklyEarnings(dp.getWeeklyEarnings().add(BigDecimal.valueOf(deliveryFee)));
        dp.setTotalCompletedOrders(dp.getTotalCompletedOrders() + 1);
        dp.setWeeklyCompletedOrders(dp.getWeeklyCompletedOrders() + 1);
        deliveryPersonRepository.save(dp);
    }

    public void resetWeeklyEarnings() {
        deliveryPersonRepository.findAll().forEach(dp -> {
            dp.setWeeklyEarnings(BigDecimal.ZERO);
            dp.setWeeklyCompletedOrders(0);
        });
    }
}
