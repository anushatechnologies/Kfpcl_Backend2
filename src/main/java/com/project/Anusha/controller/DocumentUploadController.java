package com.project.Anusha.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.project.Anusha.model.DeliveryPersonDocument;
import com.project.Anusha.service.DeliveryPersonService;
import com.project.Anusha.service.DocumentUploadService;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class DocumentUploadController {

    @Autowired
    private DeliveryPersonService deliveryPersonService;

    @Autowired
    private DocumentUploadService documentUploadService;

    /**
     * Upload document for delivery person
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("deliveryPersonId") Long deliveryPersonId,
            @RequestParam("documentType") String documentType,
            @RequestParam(value = "documentNumber", required = false) String documentNumber,
            @RequestParam("file") MultipartFile file) {
        
        try {
            // Validate document type
            DeliveryPersonDocument.DocumentType docType;
            try {
                docType = DeliveryPersonDocument.DocumentType.valueOf(documentType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid document type"));
            }

            // Validate document number
            if (!documentUploadService.validateDocumentNumber(docType, documentNumber)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid document number format"));
            }

            // Upload file
            String folderName = documentUploadService.getFolderName(docType);
            Map<String, Object> uploadResult = documentUploadService.uploadDocument(file, folderName);
            
            if (!(Boolean) uploadResult.get("success")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload document"));
            }

            // Save document record
            String documentUrl = (String) uploadResult.get("url");
            String fileName = (String) uploadResult.get("fileName");
            DeliveryPersonDocument document = deliveryPersonService.uploadDocument(
                deliveryPersonId, docType, documentUrl, documentNumber);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Document uploaded successfully");
            response.put("document", Map.of(
                "id", document.getId(),
                "documentType", document.getDocumentType(),
                "documentNumber", document.getDocumentNumber(),
                "documentUrl", document.getDocumentUrl(),
                "fileName", fileName,
                "status", document.getStatus(),
                "uploadedAt", document.getUploadedAt()
            ));
            response.put("uploadDetails", uploadResult);

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            // Document is APPROVED and locked — cannot replace
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage(), "locked", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Document upload failed: " + e.getMessage()));
        }
    }

    /**
     * Get document upload validation rules
     */
    @GetMapping("/validation-rules")
    public ResponseEntity<?> getValidationRules() {
        Map<String, Object> rules = new HashMap<>();
        
        // PAN Card rules
        rules.put("panCard", Map.of(
            "format", "5 letters + 4 digits + 1 letter (e.g., ABCDE1234F)",
            "regex", "^[A-Z]{5}[0-9]{4}[A-Z]{1}$",
            "example", "ABCDE1234F"
        ));
        
        // Aadhaar Card rules
        rules.put("aadhaarCard", Map.of(
            "format", "12 digits",
            "regex", "^\\d{12}$",
            "example", "123456789012"
        ));

        rules.put("aadhaarFront", Map.of(
            "format", "12 digits",
            "regex", "^\\d{12}$",
            "example", "123456789012"
        ));

        rules.put("aadhaarBack", Map.of(
            "format", "12 digits",
            "regex", "^\\d{12}$",
            "example", "123456789012"
        ));
        
        // Driving License rules
        rules.put("drivingLicense", Map.of(
            "format", "10-16 uppercase letters and numbers",
            "regex", "^[A-Z0-9]{10,16}$",
            "example", "DL1234567890123"
        ));

        rules.put("rcBook", Map.of(
            "format", "6-20 uppercase letters, numbers or hyphens",
            "regex", "^[A-Z0-9-]{6,20}$",
            "example", "TS09AB1234"
        ));

        rules.put("insurance", Map.of(
            "format", "Optional. If provided: 6-30 uppercase letters, numbers or hyphens",
            "regex", "^[A-Z0-9-]{6,30}$",
            "example", "POLICY-123456"
        ));
        
        // File upload rules
        rules.put("fileUpload", Map.of(
            "maxSize", "5MB",
            "allowedTypes", "image/jpeg, image/jpg, image/png, image/webp",
            "maxFiles", "1 per request"
        ));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("rules", rules);

        return ResponseEntity.ok(response);
    }

    /**
     * Delete document (only for rejected documents or re-upload)
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long documentId) {
        try {
            // This would need to be implemented in the service layer
            // For now, return a placeholder response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Document deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete document: " + e.getMessage()));
        }
    }

    /**
     * Get document by ID
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<?> getDocument(@PathVariable Long documentId) {
        try {
            // This would need to be implemented in the service layer
            // For now, return a placeholder response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Document retrieved successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve document: " + e.getMessage()));
        }
    }
}
