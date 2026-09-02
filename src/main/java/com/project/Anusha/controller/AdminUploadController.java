package com.project.Anusha.controller;

import com.project.Anusha.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/upload")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminUploadController {

    private final S3Service s3Service;

    @PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @PathVariable String type,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "admin") String folder
    ) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is required."));
            }

            String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            if ("image".equalsIgnoreCase(type) && !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please upload an image file."));
            }
            if ("video".equalsIgnoreCase(type) && !contentType.startsWith("video/")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please upload a video file."));
            }
            if (!"image".equalsIgnoreCase(type) && !"video".equalsIgnoreCase(type)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Upload type must be image or video."));
            }

            String url = s3Service.uploadFile(file, folder);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + ex.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> delete(@RequestParam("url") String url) {
        try {
            s3Service.deleteFileByUrl(url);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Delete failed: " + ex.getMessage()));
        }
    }
}
