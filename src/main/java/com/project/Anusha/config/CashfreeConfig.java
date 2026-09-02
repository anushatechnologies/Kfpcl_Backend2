package com.project.Anusha.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class CashfreeConfig {

    @Value("${cashfree.app-id}")
    private String appId;

    @Value("${cashfree.secret-key}")
    private String secretKey;

    @Value("${cashfree.webhook-secret:}")
    private String webhookSecret;

    @Value("${cashfree.api-url:https://sandbox.cashfree.com/pg}")
    private String apiUrl;

    @Value("${cashfree.api-version:2023-08-01}")
    private String apiVersion;
}
