package com.project.Anusha.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "firebase")
public class FirebaseConfig {
    private String serviceAccountPath; // path to Firebase service account JSON
    private String databaseUrl;        // Realtime Database URL

    // getters and setters
    public String getServiceAccountPath() { return serviceAccountPath; }
    public void setServiceAccountPath(String serviceAccountPath) { this.serviceAccountPath = serviceAccountPath; }

    public String getDatabaseUrl() { return databaseUrl; }
    public void setDatabaseUrl(String databaseUrl) { this.databaseUrl = databaseUrl; }
}