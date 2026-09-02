package com.project.Anusha.dto;

import lombok.Data;

@Data
public class VerifyPhoneRequest {
    private String firebaseIdToken;
    private String fcmToken;
}