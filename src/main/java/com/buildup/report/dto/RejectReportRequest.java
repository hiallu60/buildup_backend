package com.buildup.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectReportRequest(
        @NotBlank(message = "Rejection reason is required")
        @Size(max = 2000, message = "Rejection reason must not exceed 2000 characters")
        String comment
) {
}
