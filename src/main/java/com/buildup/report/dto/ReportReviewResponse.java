package com.buildup.report.dto;

import com.buildup.report.entity.Report;

public record ReportReviewResponse(
        Long reportId,
        String reviewStatus
) {
    public static ReportReviewResponse from(Report report) {
        return new ReportReviewResponse(report.getId(), report.getReviewStatus().name());
    }
}
