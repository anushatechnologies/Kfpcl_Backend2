package com.project.Anusha.dto;

public class AdminCodeVerifyRequest {
    private String challengeToken;
    private String code;

    public String getChallengeToken() { return challengeToken; }
    public void setChallengeToken(String challengeToken) { this.challengeToken = challengeToken; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
