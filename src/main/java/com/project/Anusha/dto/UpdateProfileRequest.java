package com.project.Anusha.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String name;   // optional – only updates if provided
    private String email;  // optional – only updates if provided
}
