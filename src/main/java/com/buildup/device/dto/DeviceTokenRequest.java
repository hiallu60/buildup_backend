package com.buildup.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceTokenRequest(
        @NotBlank(message = "Device token is required")
        @Size(max = 512, message = "Device token must not exceed 512 characters")
        String token
) {
}
