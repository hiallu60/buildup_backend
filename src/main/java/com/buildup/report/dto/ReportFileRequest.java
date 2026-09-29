package com.buildup.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ReportFileRequest(
        @NotBlank
        @Size(max = 500)
        String path,

        @NotBlank
        @Size(max = 255)
        String originalName,

        @NotNull
        @PositiveOrZero
        Long sizeBytes
) {
}
