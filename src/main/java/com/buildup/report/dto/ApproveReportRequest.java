package com.buildup.report.dto;

import jakarta.validation.constraints.Size;

public record ApproveReportRequest(
        @Size(max = 2000, message = "Review comment must not exceed 2000 characters")
        String comment
) {
}
