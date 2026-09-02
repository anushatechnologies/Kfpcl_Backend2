package com.project.Anusha.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public String uploadFile(MultipartFile file, String folderName) throws IOException {
        String fileName = generateFileName(file, folderName);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        PutObjectRequest request = new PutObjectRequest(bucketName, fileName, file.getInputStream(), metadata);
        amazonS3.putObject(request);

        return amazonS3.getUrl(bucketName, fileName).toString();
    }

    private String generateFileName(MultipartFile file, String folderName) {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
                
        String cleanFolderName = folderName;
        if (cleanFolderName != null && !cleanFolderName.isEmpty()) {
            if (cleanFolderName.startsWith("/")) {
                cleanFolderName = cleanFolderName.substring(1);
            }
            if (!cleanFolderName.endsWith("/")) {
                cleanFolderName = cleanFolderName + "/";
            }
        } else {
            cleanFolderName = "";
        }
        
        return cleanFolderName + UUID.randomUUID() + extension;
    }

    public void deleteFileByUrl(String fileUrl) {
        String objectKey = extractObjectKey(fileUrl);
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        amazonS3.deleteObject(bucketName, objectKey);
    }

    private String extractObjectKey(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }

        String trimmedUrl = fileUrl.trim();
        try {
            URI uri = URI.create(trimmedUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return decodeKey(trimmedUrl);
            }

            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }

            String key = path.startsWith("/") ? path.substring(1) : path;
            if (key.startsWith(bucketName + "/")) {
                key = key.substring(bucketName.length() + 1);
            }
            return decodeKey(key);
        } catch (IllegalArgumentException ex) {
            return decodeKey(trimmedUrl);
        }
    }

    private String decodeKey(String key) {
        return URLDecoder.decode(key, StandardCharsets.UTF_8);
    }
}
