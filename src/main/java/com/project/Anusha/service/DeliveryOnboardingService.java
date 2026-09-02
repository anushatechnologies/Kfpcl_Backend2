package com.project.Anusha.service;

import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.DeliveryPersonDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class DeliveryOnboardingService {

    public Map<String, Object> buildOnboardingStatus(
            DeliveryPerson deliveryPerson,
            List<DeliveryPersonDocument> documents) {
        List<ChecklistItem> checklist = buildChecklist(deliveryPerson, documents);

        List<String> requiredDocuments = new ArrayList<>();
        List<String> optionalDocuments = new ArrayList<>();
        List<String> missingRequiredDocuments = new ArrayList<>();
        List<String> missingApprovedDocuments = new ArrayList<>();

        int completedSteps = 0;
        int totalSteps = 6;

        boolean personalInfoCompleted = hasText(deliveryPerson.getFirstName()) && hasText(deliveryPerson.getLastName());
        boolean phoneVerified = deliveryPerson.isVerified();
        boolean vehicleCompleted = isVehicleDetailsComplete(deliveryPerson);
        boolean bankCompleted = isBankDetailsComplete(deliveryPerson);
        boolean profilePhotoUploaded = hasText(deliveryPerson.getProfilePhotoUrl());
        boolean profilePhotoApproved =
                deliveryPerson.getProfilePhotoStatus() == DeliveryPersonDocument.DocumentStatus.APPROVED;

        for (ChecklistItem item : checklist) {
            if ("PROFILE_PHOTO".equals(item.code())) {
                continue;
            }
            if (item.required()) {
                requiredDocuments.add(item.code());
                if (!item.uploaded()) {
                    missingRequiredDocuments.add(item.code());
                }
                if (!item.approved()) {
                    missingApprovedDocuments.add(item.code());
                }
            } else {
                optionalDocuments.add(item.code());
            }
        }

        boolean requiredDocumentsUploaded = missingRequiredDocuments.isEmpty();
        boolean requiredDocumentsApproved = missingApprovedDocuments.isEmpty();
        boolean readyForFinalApproval = phoneVerified
                && personalInfoCompleted
                && vehicleCompleted
                && bankCompleted
                && profilePhotoApproved
                && requiredDocumentsApproved;

        if (phoneVerified) completedSteps++;
        if (personalInfoCompleted) completedSteps++;
        if (vehicleCompleted) completedSteps++;
        if (bankCompleted) completedSteps++;
        if (profilePhotoUploaded) completedSteps++;
        if (requiredDocumentsUploaded) completedSteps++;

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("currentStep", determineCurrentStep(
                deliveryPerson,
                personalInfoCompleted,
                vehicleCompleted,
                bankCompleted,
                profilePhotoUploaded,
                requiredDocumentsUploaded,
                readyForFinalApproval));
        status.put("phoneVerified", phoneVerified);
        status.put("personalInfoCompleted", personalInfoCompleted);
        status.put("vehicleCompleted", vehicleCompleted);
        status.put("bankCompleted", bankCompleted);
        status.put("profilePhotoUploaded", profilePhotoUploaded);
        status.put("profilePhotoApproved", profilePhotoApproved);
        status.put("profilePhotoStatus", deliveryPerson.getProfilePhotoStatus().name());
        status.put("profilePhotoRemarks", deliveryPerson.getProfilePhotoRemarks());
        status.put("requiredDocumentsUploaded", requiredDocumentsUploaded);
        status.put("requiredDocumentsApproved", requiredDocumentsApproved);
        status.put("requiredDocuments", requiredDocuments);
        status.put("optionalDocuments", optionalDocuments);
        status.put("missingRequiredDocuments", missingRequiredDocuments);
        status.put("missingApprovedDocuments", missingApprovedDocuments);
        status.put("documentChecklist", checklist.stream().map(ChecklistItem::toMap).toList());
        status.put("readyForFinalApproval", readyForFinalApproval);
        status.put("loginAllowed", phoneVerified);
        status.put("canGoOnline", deliveryPerson.isApprovedByAdmin());
        status.put("completionPercent", Math.round((completedSteps * 100.0f) / totalSteps));
        status.put("requiredDocumentCount", requiredDocuments.size());
        status.put("approvedRequiredDocumentCount", requiredDocuments.size() - missingApprovedDocuments.size());
        status.put("personalInfo", personalInfoCompleted);
        status.put("vehicleInfo", vehicleCompleted);
        status.put("documentsUploaded", requiredDocumentsUploaded);
        status.put("documentsApproved", requiredDocumentsApproved);
        status.put("photoApproved", profilePhotoApproved);
        status.put("photoStatus", deliveryPerson.getProfilePhotoStatus());
        status.put("photoRemarks", deliveryPerson.getProfilePhotoRemarks());
        status.put("canLogin", phoneVerified);

        return status;
    }

    public boolean areRequiredDocumentsApproved(
            DeliveryPerson deliveryPerson,
            List<DeliveryPersonDocument> documents) {
        return buildChecklist(deliveryPerson, documents).stream()
                .filter(ChecklistItem::required)
                .filter(item -> !"PROFILE_PHOTO".equals(item.code()))
                .allMatch(ChecklistItem::approved);
    }

    public boolean isReadyForFinalApproval(
            DeliveryPerson deliveryPerson,
            List<DeliveryPersonDocument> documents) {
        Map<String, Object> status = buildOnboardingStatus(deliveryPerson, documents);
        Object ready = status.get("readyForFinalApproval");
        return ready instanceof Boolean && (Boolean) ready;
    }

    public boolean isBankDetailsComplete(DeliveryPerson deliveryPerson) {
        return hasText(deliveryPerson.getAccountName())
                && hasText(deliveryPerson.getAccountNumber())
                && hasText(deliveryPerson.getBankName())
                && hasText(deliveryPerson.getIfscCode());
    }

    public boolean isVehicleDetailsComplete(DeliveryPerson deliveryPerson) {
        boolean hasVehicleType = deliveryPerson.getVehicleType() != null;
        boolean hasVehicleModel = hasText(deliveryPerson.getVehicleModel())
                && !"Not Specified".equalsIgnoreCase(deliveryPerson.getVehicleModel());

        if (!hasVehicleType || !hasVehicleModel) {
            return false;
        }

        if (deliveryPerson.getVehicleType() == DeliveryPerson.VehicleType.EV) {
            return true; // EV riders don't need a registration number
        }

        return hasText(deliveryPerson.getRegistrationNumber())
                && !deliveryPerson.getRegistrationNumber().startsWith("TEMP-");
    }

    private List<ChecklistItem> buildChecklist(
            DeliveryPerson deliveryPerson,
            List<DeliveryPersonDocument> documents) {
        List<ChecklistItem> checklist = new ArrayList<>();

        checklist.add(buildProfilePhotoChecklistItem(deliveryPerson));

        for (String code : getRequiredDocumentCodes(deliveryPerson)) {
            checklist.add(buildDocumentChecklistItem(code, true, documents));
        }
        for (String code : getOptionalDocumentCodes(deliveryPerson)) {
            checklist.add(buildDocumentChecklistItem(code, false, documents));
        }

        return checklist;
    }

    private ChecklistItem buildProfilePhotoChecklistItem(DeliveryPerson deliveryPerson) {
        boolean uploaded = hasText(deliveryPerson.getProfilePhotoUrl());
        boolean approved = deliveryPerson.getProfilePhotoStatus() == DeliveryPersonDocument.DocumentStatus.APPROVED;

        return new ChecklistItem(
                "PROFILE_PHOTO",
                "Profile Photo",
                true,
                uploaded,
                approved,
                deliveryPerson.getProfilePhotoStatus().name(),
                deliveryPerson.getProfilePhotoRemarks(),
                deliveryPerson.getProfilePhotoUrl(),
                null);
    }

    private ChecklistItem buildDocumentChecklistItem(
            String code,
            boolean required,
            List<DeliveryPersonDocument> documents) {
        Optional<DeliveryPersonDocument> document = resolveRequirementDocument(code, documents);
        boolean uploaded = document.isPresent() && hasText(document.get().getDocumentUrl());
        boolean approved = document.isPresent()
                && document.get().getStatus() == DeliveryPersonDocument.DocumentStatus.APPROVED;

        return new ChecklistItem(
                code,
                humanize(code),
                required,
                uploaded,
                approved,
                document.map(value -> value.getStatus().name()).orElse("NOT_UPLOADED"),
                document.map(DeliveryPersonDocument::getAdminRemarks).orElse(null),
                document.map(DeliveryPersonDocument::getDocumentUrl).orElse(null),
                document.map(DeliveryPersonDocument::getDocumentNumber).orElse(null));
    }

    private Optional<DeliveryPersonDocument> resolveRequirementDocument(
            String code,
            List<DeliveryPersonDocument> documents) {
        return switch (code) {
            case "AADHAAR_FRONT" -> findFirstDocument(documents,
                    EnumSet.of(
                            DeliveryPersonDocument.DocumentType.AADHAAR_FRONT,
                            DeliveryPersonDocument.DocumentType.AADHAAR_CARD));
            case "AADHAAR_BACK" -> findFirstDocument(documents,
                    EnumSet.of(
                            DeliveryPersonDocument.DocumentType.AADHAAR_BACK,
                            DeliveryPersonDocument.DocumentType.AADHAAR_CARD));
            case "PAN_CARD" -> findFirstDocument(documents,
                    EnumSet.of(DeliveryPersonDocument.DocumentType.PAN_CARD));
            case "DRIVING_LICENSE" -> findFirstDocument(documents,
                    EnumSet.of(DeliveryPersonDocument.DocumentType.DRIVING_LICENSE));
            case "RC_BOOK" -> findFirstDocument(documents,
                    EnumSet.of(DeliveryPersonDocument.DocumentType.RC_BOOK));
            case "INSURANCE" -> findFirstDocument(documents,
                    EnumSet.of(DeliveryPersonDocument.DocumentType.INSURANCE));
            default -> Optional.empty();
        };
    }

    private Optional<DeliveryPersonDocument> findFirstDocument(
            List<DeliveryPersonDocument> documents,
            Set<DeliveryPersonDocument.DocumentType> acceptedTypes) {
        return documents.stream()
                .filter(document -> acceptedTypes.contains(document.getDocumentType()))
                .sorted((left, right) -> right.getUploadedAt().compareTo(left.getUploadedAt()))
                .findFirst();
    }

    private List<String> getRequiredDocumentCodes(DeliveryPerson deliveryPerson) {
        List<String> requiredCodes = new ArrayList<>();
        requiredCodes.add("AADHAAR_FRONT");
        requiredCodes.add("AADHAAR_BACK");
        requiredCodes.add("PAN_CARD");

        if (isMotorVehicle(deliveryPerson.getVehicleType())) {
            requiredCodes.add("DRIVING_LICENSE");
            requiredCodes.add("RC_BOOK");
        }

        return requiredCodes;
    }

    private List<String> getOptionalDocumentCodes(DeliveryPerson deliveryPerson) {
        List<String> optionalCodes = new ArrayList<>();
        if (isMotorVehicle(deliveryPerson.getVehicleType())) {
            optionalCodes.add("INSURANCE");
        }
        return optionalCodes;
    }

    private boolean isMotorVehicle(DeliveryPerson.VehicleType vehicleType) {
        return vehicleType != null && switch (vehicleType) {
            case BIKE, SCOOTY, AUTO, HEAVY -> true;
            case EV -> false; // EV riders don't need RC / insurance
        };
    }

    private String determineCurrentStep(
            DeliveryPerson deliveryPerson,
            boolean personalInfoCompleted,
            boolean vehicleCompleted,
            boolean bankCompleted,
            boolean profilePhotoUploaded,
            boolean requiredDocumentsUploaded,
            boolean readyForFinalApproval) {
        if (deliveryPerson.isApprovedByAdmin()) {
            return "APPROVED";
        }

        if (deliveryPerson.getApprovalStatus() == DeliveryPerson.ApprovalStatus.REJECTED
                || deliveryPerson.getProfilePhotoStatus() == DeliveryPersonDocument.DocumentStatus.REJECTED) {
            return "REJECTED";
        }

        if (readyForFinalApproval) {
            return "KYC_UNDER_REVIEW";
        }

        if (personalInfoCompleted && vehicleCompleted && bankCompleted && profilePhotoUploaded && requiredDocumentsUploaded) {
            return "DOCUMENTS_UPLOADED";
        }

        if (personalInfoCompleted && vehicleCompleted) {
            return "PROFILE_COMPLETED";
        }

        if (deliveryPerson.isVerified()) {
            return "OTP_VERIFIED";
        }

        return "ACCOUNT_CREATED";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String humanize(String code) {
        return switch (code) {
            case "AADHAAR_FRONT" -> "Aadhaar Front";
            case "AADHAAR_BACK" -> "Aadhaar Back";
            case "PAN_CARD" -> "PAN Card";
            case "DRIVING_LICENSE" -> "Driving License";
            case "RC_BOOK" -> "RC Book";
            case "INSURANCE" -> "Insurance";
            case "PROFILE_PHOTO" -> "Profile Photo";
            default -> code.replace('_', ' ');
        };
    }

    private record ChecklistItem(
            String code,
            String label,
            boolean required,
            boolean uploaded,
            boolean approved,
            String status,
            String remarks,
            String documentUrl,
            String documentNumber) {

        private Map<String, Object> toMap() {
            Map<String, Object> item = new HashMap<>();
            item.put("code", code);
            item.put("label", label);
            item.put("required", required);
            item.put("uploaded", uploaded);
            item.put("approved", approved);
            item.put("status", status);
            item.put("remarks", remarks);
            item.put("documentUrl", documentUrl);
            item.put("documentNumber", documentNumber);
            return item;
        }
    }
}
