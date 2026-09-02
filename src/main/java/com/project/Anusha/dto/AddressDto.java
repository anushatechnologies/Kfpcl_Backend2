package com.project.Anusha.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddressDto {
    private Long id;

    private String addressType;

    private String flatNumber;

    @NotBlank(message = "addressLine1 is required")
    private String addressLine1;

    private String addressLine2;
    private String landmark;

    @NotBlank(message = "city is required")
    private String city;

    private String state;

    @NotBlank(message = "postalCode is required")
    @Pattern(regexp = "^\\d{6}$", message = "postalCode must be a valid 6-digit PIN code")
    private String postalCode;

    @NotNull(message = "latitude is required — get from device GPS")
    @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90.0",  message = "latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "longitude is required — get from device GPS")
    @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180.0",  message = "longitude must be between -180 and 180")
    private Double longitude;

    private Boolean isDefault = false;

    private String contactName;
    private String contactPhone;
}
