package com.project.Anusha.dto;

import lombok.Data;

@Data
public class WhatsAppTemplateRequest {
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
}
