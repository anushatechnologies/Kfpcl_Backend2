package com.project.Anusha.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.Anusha.dto.WhatsAppTemplateRequest;
import com.project.Anusha.dto.WhatsAppTemplateResponse;
import com.project.Anusha.service.WhatsAppService;
import com.project.Anusha.service.WhatsAppTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/whatsapp-templates")
public class WhatsAppTemplateController {

    private final WhatsAppTemplateService whatsAppTemplateService;
    private final WhatsAppService whatsAppService;

    @PostMapping
    public ResponseEntity<WhatsAppTemplateResponse> createTemplate(@RequestBody WhatsAppTemplateRequest request) {
        return ResponseEntity.ok(whatsAppTemplateService.createTemplate(request));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<WhatsAppTemplateResponse> updateTemplate(@PathVariable Long templateId,
            @RequestBody WhatsAppTemplateRequest request) {
        return ResponseEntity.ok(whatsAppTemplateService.updateTemplate(templateId, request));
    }

    @PostMapping("/{templateId}/submit")
    public ResponseEntity<WhatsAppTemplateResponse> submitForReview(@PathVariable Long templateId) {
        return ResponseEntity.ok(whatsAppTemplateService.submitForReview(templateId));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<WhatsAppTemplateResponse> getTemplate(@PathVariable Long templateId) {
        WhatsAppTemplateResponse response = whatsAppTemplateService.getTemplate(templateId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/admin/whatsapp-templates/meta-templates
     *
     * Lists all message templates from Meta Business Suite for this WABA.
     * Requires WHATSAPP_WABA_ID env var to be set on the server.
     *
     * Returns the raw Meta Graph API response:
     *   { "data": [ { "name": "...", "status": "APPROVED", "category": "...", "language": "..." }, ... ] }
     */
    @GetMapping("/meta-templates")
    public ResponseEntity<?> getMetaTemplates() {
        try {
            JsonNode result = whatsAppService.fetchMetaTemplates();
            return ResponseEntity.ok(result);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "success", false,
                    "message", ex.getMessage()));
        }
    }

    /**
     * POST /api/admin/whatsapp-templates/send
     *
     * Send a WhatsApp campaign using an existing Meta-approved template.
     *
     * @param templateName The name of the Meta template (e.g., "scan2paper_shop_invitation")
     * @param file         CSV/XLSX with recipients (Phone Number, Name, Active columns)
     * @param activeOnly   If true, skip rows where Active is false
     * @return Campaign ID and status
     */
    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> sendCampaign(
            @RequestParam("templateName") String templateName,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly) {
        try {
            WhatsAppTemplateService.CampaignSendResponse response = whatsAppTemplateService.sendCampaign(templateName,
                    file, activeOnly);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to read the uploaded file."));
        }
    }
}
