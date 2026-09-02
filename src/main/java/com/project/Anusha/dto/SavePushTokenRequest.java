package com.project.Anusha.dto;

import jakarta.validation.constraints.NotBlank;

public record SavePushTokenRequest(
        @NotBlank String expoPushToken,
        @NotBlank String platform,
        @NotBlank String appType
) {}
