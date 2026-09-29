package com.buildup.report.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateReportRequest(
        @NotBlank
        @Size(max = 50)
        String processKey,

        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal toProgress,

        @Size(max = 10000)
        String memo,

        @Size(max = 100)
        String weather,

        @Min(0)
        Integer workers,

        @Size(max = 10000)
        String equipment,

        @Size(max = 20)
        List<@NotBlank @Size(max = 255) String> photoFileNames,

        @Size(max = 20)
        List<@Valid ReportFileRequest> files
) {
}
