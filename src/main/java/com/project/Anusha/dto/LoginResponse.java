package com.project.Anusha.dto;

import java.time.LocalDateTime;
import java.util.List;

public class LoginResponse {
    private String token;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private Long id;
    private String email;
    private String role;
    private String name;
    private boolean mustChangePassword;
    private boolean adminCodeRequired;
    private String adminCodeChallengeToken;
    private LocalDateTime adminAccessExpiresAt;
    private Long adminAccessRemainingSeconds;
    private boolean adminAccessExpired;
    private List<String> permissions;

	public String getToken() { return token; }
	public void setToken(String token) { this.token = token; }

	public String getAccessToken() { return accessToken; }
	public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

	public String getRefreshToken() { return refreshToken; }
	public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

	public long getExpiresIn() { return expiresIn; }
	public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public String getRole() { return role; }
	public void setRole(String role) { this.role = role; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public boolean isMustChangePassword() { return mustChangePassword; }
	public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public boolean isAdminCodeRequired() { return adminCodeRequired; }
    public void setAdminCodeRequired(boolean adminCodeRequired) { this.adminCodeRequired = adminCodeRequired; }

    public String getAdminCodeChallengeToken() { return adminCodeChallengeToken; }
    public void setAdminCodeChallengeToken(String adminCodeChallengeToken) {
        this.adminCodeChallengeToken = adminCodeChallengeToken;
    }

    public LocalDateTime getAdminAccessExpiresAt() { return adminAccessExpiresAt; }
    public void setAdminAccessExpiresAt(LocalDateTime adminAccessExpiresAt) {
        this.adminAccessExpiresAt = adminAccessExpiresAt;
    }

    public Long getAdminAccessRemainingSeconds() { return adminAccessRemainingSeconds; }
    public void setAdminAccessRemainingSeconds(Long adminAccessRemainingSeconds) {
        this.adminAccessRemainingSeconds = adminAccessRemainingSeconds;
    }

    public boolean isAdminAccessExpired() { return adminAccessExpired; }
    public void setAdminAccessExpired(boolean adminAccessExpired) {
        this.adminAccessExpired = adminAccessExpired;
    }

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }

	public LoginResponse(String token, String refreshToken, long expiresIn,
	                     Long id, String email, String role, String name, boolean mustChangePassword,
                         List<String> permissions) {
		this.token = token;
		this.accessToken = token;
		this.refreshToken = refreshToken;
		this.expiresIn = expiresIn;
		this.id = id;
		this.email = email;
		this.role = role;
		this.name = name;
        this.mustChangePassword = mustChangePassword;
        this.adminCodeRequired = false;
        this.adminCodeChallengeToken = null;
        this.permissions = permissions;
	}

    public LoginResponse(Long id, String email, String role, String name,
                         boolean mustChangePassword, String adminCodeChallengeToken,
                         List<String> permissions) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.name = name;
        this.mustChangePassword = mustChangePassword;
        this.adminCodeRequired = true;
        this.adminCodeChallengeToken = adminCodeChallengeToken;
        this.permissions = permissions;
    }
}
