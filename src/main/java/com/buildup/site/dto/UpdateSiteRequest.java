package com.buildup.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record UpdateSiteRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 255)
        String address,

        @Size(max = 100)
        String workType,

        @Digits(integer = 3, fraction = 7)
        BigDecimal latitude,

        @Digits(integer = 3, fraction = 7)
        BigDecimal longitude
) {
}
