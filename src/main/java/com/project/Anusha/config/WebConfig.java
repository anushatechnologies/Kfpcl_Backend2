package com.project.Anusha.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

// CORS is handled entirely by Spring Security's CorsFilter in SecurityConfig.
// Do NOT define addCorsMappings here — dual CORS configs conflict and cause
// the browser to receive responses with no Access-Control-Allow-Origin header.
@Configuration
@EnableAsync
public class WebConfig implements WebMvcConfigurer {

    /**
     * Shared RestTemplate with explicit timeouts.
     * Spring Boot's default RestTemplate has NO timeout — a slow upstream
     * (Expo push, Razorpay, etc.) would otherwise block the request thread
     * for the OS TCP default (~75 s on Linux).
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(8))
                .build();
    }

    /**
     * Force JSON as the default content type for all API responses.
     * Ignores the browser's Accept: application/xml header so the API
     * always returns JSON regardless of client (browser, Postman, app).
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
            .favorParameter(false)
            .ignoreAcceptHeader(true)
            .defaultContentType(MediaType.APPLICATION_JSON);
    }
}
