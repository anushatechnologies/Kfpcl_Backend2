package com.project.Anusha.dto;

import lombok.Data;

@Data
public class DeliverySignupRequest {
    private String firebaseIdToken;
    private String firstName;
    private String lastName;
    private String vehicleType;
    private String vehicleModel;
    private String registrationNumber;
    private String profilePhotoUrl;
    private String fcmToken;
}