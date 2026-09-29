package com.buildup.site.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSiteProcessRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String processKey,

        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        @DecimalMin("0.01")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal weight,

        LocalDate planStart,
        LocalDate planEnd,

        @NotNull
        @Min(0)
        Integer sortOrder
) {
}
