package com.project.Anusha.dto;

import lombok.Data;

@Data
public class WhatsAppTemplateResponse {
    private Long id;
    private String name;
    private String language;
    private String category;
    private String headerType;
    private String headerText;
    private String bodyText;
    private String footerText;
    private String websiteUrl;
    private String phoneNumber;
    private String validityPeriod;
    private String status;
    private String reviewNotes;
}
