package com.project.Anusha.controller;

import com.project.Anusha.model.Banner;
import com.project.Anusha.repository.BannerRepository;
import com.project.Anusha.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/banners")
@CrossOrigin(origins = "*")
public class BannerAdminController {

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private S3Service s3Service;

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createBanner(
            @RequestParam("name") String name,
            @RequestParam(value = "targetUrl", required = false) String targetUrl,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "actionValue", required = false) String actionValue,
            @RequestParam(value = "displayOrder", defaultValue = "0") Integer displayOrder,
            @RequestParam(value = "targetApp", defaultValue = "CUSTOMER") String targetApp,
            @RequestParam(value = "image", required = false) org.springframework.web.multipart.MultipartFile image,
            @RequestParam(value = "video", required = false) org.springframework.web.multipart.MultipartFile video) {

        try {
            Banner banner = new Banner();
            banner.setName(name);
            banner.setTargetUrl(targetUrl);
            banner.setActionType(actionType);
            banner.setActionValue(actionValue);
            banner.setDisplayOrder(displayOrder);
            banner.setTargetApp(targetApp != null ? targetApp : "CUSTOMER");
            banner.setIsActive(true);

            if (image != null && !image.isEmpty()) {
                String imageUrl = s3Service.uploadFile(image, "banners");
                banner.setImageUrl(imageUrl);
            }

            if (video != null && !video.isEmpty()) {
                String videoUrl = s3Service.uploadFile(video, "banners");
                banner.setVideoUrl(videoUrl);
            }

            Banner savedBanner = bannerRepository.save(banner);
            return ResponseEntity
                    .ok(Map.of("success", true, "message", "Banner created successfully", "banner", savedBanner));
        } catch (Exception e) {
            System.err.println("❌ Banner create failed: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload banner: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllBanners() {
        List<Banner> banners = bannerRepository.findAll();
        return ResponseEntity.ok(Map.of("success", true, "banners", banners));
    }

    @PutMapping(value = "/{id}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateBanner(
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam(value = "targetUrl", required = false) String targetUrl,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "actionValue", required = false) String actionValue,
            @RequestParam(value = "displayOrder", defaultValue = "0") Integer displayOrder,
            @RequestParam(value = "targetApp", defaultValue = "CUSTOMER") String targetApp,
            @RequestParam(value = "image", required = false) org.springframework.web.multipart.MultipartFile image,
            @RequestParam(value = "video", required = false) org.springframework.web.multipart.MultipartFile video) {

        try {
            Banner banner = bannerRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Banner not found"));

            banner.setName(name);
            banner.setTargetUrl(targetUrl);
            banner.setActionType(actionType);
            banner.setActionValue(actionValue);
            banner.setDisplayOrder(displayOrder);
            banner.setTargetApp(targetApp != null ? targetApp : "CUSTOMER");

            if (image != null && !image.isEmpty()) {
                String imageUrl = s3Service.uploadFile(image, "banners");
                banner.setImageUrl(imageUrl);
            }

            if (video != null && !video.isEmpty()) {
                String videoUrl = s3Service.uploadFile(video, "banners");
                banner.setVideoUrl(videoUrl);
            }

            Banner savedBanner = bannerRepository.save(banner);
            return ResponseEntity
                    .ok(Map.of("success", true, "message", "Banner updated successfully", "banner", savedBanner));
        } catch (Exception e) {
            System.err.println("❌ Banner update failed: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update banner: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBanner(@PathVariable Long id) {
        if (bannerRepository.existsById(id)) {
            bannerRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Banner deleted successfully"));
        } else {
            return ResponseEntity.status(404).body(Map.of("error", "Banner not found"));
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> toggleBannerStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> statusData) {
        Boolean isActive = statusData.get("isActive");
        if (isActive == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "isActive status is required"));
        }

        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Banner not found"));
        banner.setIsActive(isActive);
        bannerRepository.save(banner);

        return ResponseEntity
                .ok(Map.of("success", true, "message", "Banner status updated successfully", "banner", banner));
    }
}
