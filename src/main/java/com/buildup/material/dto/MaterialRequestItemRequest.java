package com.buildup.material.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MaterialRequestItemRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        @DecimalMin(value = "0.001")
        @Digits(integer = 12, fraction = 3)
        BigDecimal quantity,

        @NotBlank
        @Size(max = 30)
        String unit
) {
}
