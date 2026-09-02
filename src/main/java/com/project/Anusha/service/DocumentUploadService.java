package com.project.Anusha.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.project.Anusha.model.DeliveryPersonDocument;

@Service
public class DocumentUploadService {

    @Autowired
    private AmazonS3 amazonS3;

    @org.springframework.beans.factory.annotation.Value("${aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Upload document to S3 and return the URL
     */
    public Map<String, Object> uploadDocument(MultipartFile file, String folderName) throws IOException {
        try {
            // Validate file
            if (file.isEmpty()) {
                throw new IllegalArgumentException("File is empty");
            }

            // Validate file size (max 5MB)
            if (file.getSize() > 5 * 1024 * 1024) {
                throw new IllegalArgumentException("File size exceeds 5MB limit");
            }

            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Only image files are allowed");
            }

            // Generate unique file name
            String fileName = generateUniqueFileName(file.getOriginalFilename(), folderName);

            // Set metadata
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);
            metadata.addUserMetadata("original-name", file.getOriginalFilename());
            metadata.addUserMetadata("upload-time", String.valueOf(System.currentTimeMillis()));

            // Upload to S3 with public read so avatar URLs work in browser
            PutObjectRequest putRequest = new PutObjectRequest(bucketName, fileName, file.getInputStream(), metadata);
            amazonS3.putObject(putRequest);

            // Generate file URL
            String fileUrl = amazonS3.getUrl(bucketName, fileName).toString();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("url", fileUrl);
            response.put("fileName", fileName);
            response.put("bucket", bucketName);
            response.put("format", getFileExtension(file.getOriginalFilename()));
            response.put("size", file.getSize());

            return response;

        } catch (IOException e) {
            throw new IOException("Failed to upload document: " + e.getMessage());
        }
    }

    /**
     * Delete document from S3
     */
    public boolean deleteDocument(String fileName) {
        try {
            amazonS3.deleteObject(bucketName, fileName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get folder name based on document type
     */
    public String getFolderName(DeliveryPersonDocument.DocumentType documentType) {
        switch (documentType) {
            case PROFILE_PHOTO:
                return "delivery-boys/profile-photos";
            case PAN_CARD:
                return "delivery-boys/pan";
            case AADHAAR_CARD:
                // Legacy type — keep using same folder as AADHAAR_FRONT
                return "delivery-boys/aadhar";
            case AADHAAR_FRONT:
                return "delivery-boys/aadhar";          // reuse existing S3 folder
            case AADHAAR_BACK:
                return "delivery-boys/aadhar";          // reuse existing S3 folder
            case RC_BOOK:
                return "delivery-boys/rc-book";
            case INSURANCE:
                return "delivery-boys/insurance";
            case DRIVING_LICENSE:
                return "delivery-boys/driving-license";
            default:
                return "delivery-boys/others";
        }
    }

    /**
     * Validate document number based on document type.
     *
     * EV (Electric Bike) rules:
     *   - RC_BOOK   : not required for EV — always returns true to skip validation.
     *   - INSURANCE : optional for all vehicles — always returns true when blank.
     */
    public boolean validateDocumentNumber(DeliveryPersonDocument.DocumentType documentType, String documentNumber) {
        String normalizedNumber = documentNumber != null ? documentNumber.trim() : "";

        // INSURANCE is always optional — blank is fine for every vehicle type.
        if (documentType == DeliveryPersonDocument.DocumentType.INSURANCE && normalizedNumber.isEmpty()) {
            return true;
        }

        // RC_BOOK is skipped for EV (Electric Bike).
        // The mobile app sends documentNumber="EV_EXEMPT" as a sentinel so we can skip validation.
        if (documentType == DeliveryPersonDocument.DocumentType.RC_BOOK
                && "EV_EXEMPT".equals(normalizedNumber)) {
            return true;
        }

        if (normalizedNumber.isEmpty()) {
            return false;
        }

        switch (documentType) {
            case PROFILE_PHOTO:
                // Document number for profile photo is the delivery person's phone number (10 digits)
                return normalizedNumber.matches("^\\d{10}$");

            case AADHAAR_CARD:
            case AADHAAR_FRONT:
            case AADHAAR_BACK:
                // Aadhaar: exactly 12 digits (spaces already stripped by mobile app)
                return normalizedNumber.matches("^\\d{12}$");

            case PAN_CARD:
                // PAN: 5 letters + 4 digits + 1 letter  e.g. ABCDE1234F
                return normalizedNumber.matches("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");

            case DRIVING_LICENSE:
                // DL formats vary by state (10-16 alphanumeric chars)
                return normalizedNumber.matches("^[A-Z0-9]{10,16}$");

            case RC_BOOK:
                return normalizedNumber.matches("^[A-Z0-9-]{6,20}$");

            case INSURANCE:
                return normalizedNumber.matches("^[A-Z0-9-]{6,30}$");

            default:
                return false;
        }
    }

    /**
     * Extract document metadata
     */
    public Map<String, Object> extractDocumentMetadata(MultipartFile file) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("originalName", file.getOriginalFilename());
        metadata.put("contentType", file.getContentType());
        metadata.put("size", file.getSize());
        metadata.put("uploadTime", System.currentTimeMillis());
        return metadata;
    }

    /**
     * Generate unique file name for S3
     */
    private String generateUniqueFileName(String originalFileName, String folderName) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomSuffix = String.format("%04d", (int)(Math.random() * 10000));
        String fileExtension = getFileExtension(originalFileName);
        
        return String.format("%s/%s_%s_%s.%s", 
            folderName, 
            timestamp, 
            randomSuffix, 
            originalFileName != null ? originalFileName.replaceAll("[^a-zA-Z0-9.-]", "_") : "file",
            fileExtension);
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "jpg";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "jpg";
        }
        
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * Check if bucket exists, create if not
     */
    public void ensureBucketExists() {
        if (!amazonS3.doesBucketExistV2(bucketName)) {
            amazonS3.createBucket(bucketName);
        }
    }
}
