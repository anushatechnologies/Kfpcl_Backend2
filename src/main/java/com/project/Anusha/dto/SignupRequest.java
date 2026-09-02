package com.project.Anusha.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String firebaseIdToken;
    private String name;     // optional
    private String email;    // optional
    private String fcmToken; // optional
    private String referralCode; // optional — code from share link, used at first signup only
    private String deviceId;     // optional — for fraud checks only
}