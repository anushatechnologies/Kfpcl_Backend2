package com.project.Anusha.controller;

import com.project.Anusha.service.WhatsAppCampaignService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/notifications/whatsapp-campaigns")
@CrossOrigin(origins = "*")
public class WhatsAppCampaignController {

    private final WhatsAppCampaignService whatsAppCampaignService;

    public WhatsAppCampaignController(WhatsAppCampaignService whatsAppCampaignService) {
        this.whatsAppCampaignService = whatsAppCampaignService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> startCampaign(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateName") String templateName,
            @RequestParam(value = "headerImageUrl", required = false) String headerImageUrl,
            @RequestParam(value = "headerMediaUrl", required = false) String headerMediaUrl,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly
    ) {
        try {
            WhatsAppCampaignService.CampaignStartResponse response = whatsAppCampaignService.startCampaign(
                    file,
                    templateName,
                    headerMediaUrl != null && !headerMediaUrl.isBlank() ? headerMediaUrl : headerImageUrl,
                    activeOnly
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to read the uploaded file."
            ));
        }
    }

    @PostMapping(value = "/app-video-v1", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> startAppVideoCampaign(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "headerMediaUrl", required = false) String headerMediaUrl,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly
    ) {
        try {
            WhatsAppCampaignService.CampaignStartResponse response = whatsAppCampaignService.startAppVideoCampaign(
                    file,
                    headerMediaUrl,
                    activeOnly
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to read the uploaded file."
            ));
        }
    }

    /**
     * POST /api/admin/notifications/whatsapp-campaigns/refer-and-earn-v1
     *
     * Sends the approved Meta template `refer_and_earn_invite` to every phone
     * in the uploaded CSV/XLSX. The {{1}} body variable (referral link) is
     * auto-resolved per recipient by phone number lookup.
     *
     * Required form fields:
     *   - file:           CSV or XLSX with "Phone Number" + "Name" columns
     *   - headerMediaUrl: public URL of the video (S3, etc.) — OR
     *   - headerMediaFile: video file uploaded inline
     *   - activeOnly:     true/false (default true)
     */
    @PostMapping(value = "/refer-and-earn-v1", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> startReferAndEarnCampaign(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "headerMediaUrl", required = false) String headerMediaUrl,
            @RequestParam(value = "headerMediaFile", required = false) MultipartFile headerMediaFile,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly
    ) {
        try {
            WhatsAppCampaignService.CampaignStartResponse response =
                    whatsAppCampaignService.startReferAndEarnCampaign(
                            file, headerMediaUrl, headerMediaFile, activeOnly);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to read the uploaded file."
            ));
        }
    }

    @PostMapping(value = "/global-hiring-v1", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> startGlobalHiringCampaign(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "headerMediaUrl", required = false) String headerMediaUrl,
            @RequestParam(value = "headerMediaFile", required = false) MultipartFile headerMediaFile,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly
    ) {
        try {
            WhatsAppCampaignService.CampaignStartResponse response =
                    whatsAppCampaignService.startGlobalHiringCampaign(
                            file, headerMediaUrl, headerMediaFile, activeOnly);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to read the uploaded file."
            ));
        }
    }

    /**
     * POST /api/admin/notifications/whatsapp-campaigns/referral-template
     *
     * Generic referral-link blast — works with ANY approved Meta template
     * whose body has a single {{1}} variable for the referral link.
     *
     * Supports image OR video OR no header. Used by the "lucky" template
     * (image header) and any future referral campaign with the same shape.
     *
     * Form fields:
     *   - file:            CSV/XLSX with Phone Number + Name (+ optional Active)
     *   - templateName:    Meta-approved template name (e.g. "lucky")
     *   - headerType:      "image" | "video" | "none" (default "image")
     *   - headerMediaUrl:  public URL of the header media (image or video) — OR
     *   - headerMediaFile: inline image/video file upload
     *   - activeOnly:      true/false (default true)
     */
    @PostMapping(value = "/referral-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> startReferralTemplateCampaign(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateName") String templateName,
            @RequestParam(value = "headerType", defaultValue = "image") String headerType,
            @RequestParam(value = "headerMediaUrl", required = false) String headerMediaUrl,
            @RequestParam(value = "headerMediaFile", required = false) MultipartFile headerMediaFile,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly
    ) {
        try {
            WhatsAppCampaignService.CampaignStartResponse response =
                    whatsAppCampaignService.startReferralTemplateCampaign(
                            file, templateName, headerType, headerMediaUrl, headerMediaFile, activeOnly);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to read the uploaded file."
            ));
        }
    }

    /**
     * POST /api/admin/notifications/whatsapp-campaigns/test-single
     *
     * Diagnostic — sends ONE template to ONE phone, returns Meta's full response
     * including error code/subcode if it fails. Use this to debug 131049 etc.
     * without consuming the 24-hour marketing cap on more numbers.
     */
    @PostMapping("/test-single")
    public ResponseEntity<?> testSingle(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String templateName = body.getOrDefault("templateName", "refer_and_earn_invite");
        String headerMediaUrl = body.get("headerMediaUrl");
        Map<String, Object> result = whatsAppCampaignService.sendSingleTest(phone, templateName, headerMediaUrl);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<?> getCampaignStatus(@PathVariable String campaignId) {
        try {
            return ResponseEntity.ok(whatsAppCampaignService.getCampaignStatus(campaignId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }
}
