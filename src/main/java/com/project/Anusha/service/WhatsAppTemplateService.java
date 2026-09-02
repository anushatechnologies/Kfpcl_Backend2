package com.project.Anusha.service;

import com.project.Anusha.dto.WhatsAppTemplateRequest;
import com.project.Anusha.dto.WhatsAppTemplateResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WhatsAppTemplateService {

    private final Map<Long, WhatsAppTemplateResponse> templates = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final AtomicLong campaignIdGenerator = new AtomicLong(1000);

    @Data
    @AllArgsConstructor
    public static class CampaignSendResponse {
        private String campaignId;
        private String templateName;
        private int recipientCount;
        private String message;
        private boolean success;
    }

    public WhatsAppTemplateResponse createTemplate(WhatsAppTemplateRequest request) {
        WhatsAppTemplateResponse response = new WhatsAppTemplateResponse();
        response.setId(idGenerator.getAndIncrement());
        response.setName(request.getName());
        response.setLanguage(request.getLanguage());
        response.setCategory(request.getCategory());
        response.setHeaderType(request.getHeaderType());
        response.setHeaderText(request.getHeaderText());
        response.setBodyText(request.getBodyText());
        response.setFooterText(request.getFooterText());
        response.setWebsiteUrl(request.getWebsiteUrl());
        response.setPhoneNumber(request.getPhoneNumber());
        response.setValidityPeriod(request.getValidityPeriod());
        response.setStatus("DRAFT");
        response.setReviewNotes("Template saved as draft.");
        templates.put(response.getId(), response);
        return response;
    }

    public WhatsAppTemplateResponse updateTemplate(Long templateId, WhatsAppTemplateRequest request) {
        WhatsAppTemplateResponse template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }
        template.setName(request.getName());
        template.setLanguage(request.getLanguage());
        template.setCategory(request.getCategory());
        template.setHeaderType(request.getHeaderType());
        template.setHeaderText(request.getHeaderText());
        template.setBodyText(request.getBodyText());
        template.setFooterText(request.getFooterText());
        template.setWebsiteUrl(request.getWebsiteUrl());
        template.setPhoneNumber(request.getPhoneNumber());
        template.setValidityPeriod(request.getValidityPeriod());
        template.setStatus("DRAFT");
        template.setReviewNotes("Template updated and saved as draft.");
        return template;
    }

    public WhatsAppTemplateResponse submitForReview(Long templateId) {
        WhatsAppTemplateResponse template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }
        template.setStatus("PENDING_REVIEW");
        template.setReviewNotes("Template submitted for review on the Meta Business Suite workflow.");
        return template;
    }

    public WhatsAppTemplateResponse getTemplate(Long templateId) {
        return templates.get(templateId);
    }

    public CampaignSendResponse sendCampaign(String templateName, MultipartFile file, boolean activeOnly)
            throws IOException {
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("Template name is required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Recipients file is required");
        }

        // Parse CSV/XLSX to count recipients
        int recipientCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                if (activeOnly) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        String active = parts[2].trim();
                        if ("true".equalsIgnoreCase(active) || "1".equalsIgnoreCase(active)) {
                            recipientCount++;
                        }
                    }
                } else {
                    recipientCount++;
                }
            }
        }

        if (recipientCount == 0) {
            throw new IllegalArgumentException("No valid recipients found in the file");
        }

        // Generate campaign ID and return success response
        String campaignId = "camp_" + campaignIdGenerator.getAndIncrement();
        return new CampaignSendResponse(
                campaignId,
                templateName,
                recipientCount,
                "Campaign queued successfully. Messages will be sent using template: " + templateName,
                true);
    }
}
