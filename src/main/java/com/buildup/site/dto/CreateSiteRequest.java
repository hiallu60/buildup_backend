package com.buildup.site.dto;

import com.buildup.site.entity.ProgressCalculationMode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record CreateSiteRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 255)
        String address,

        @Size(max = 100)
        String workType,

        @Positive
        Long companyId,

        @Digits(integer = 3, fraction = 7)
        BigDecimal latitude,

        @Digits(integer = 3, fraction = 7)
        BigDecimal longitude,

        ProgressCalculationMode progressCalculationMode
) {
}
