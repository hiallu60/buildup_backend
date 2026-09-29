package com.buildup.report.dto;

import com.buildup.report.entity.Report;
import com.buildup.report.entity.ReportReviewStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReportSummaryResponse(
        Long id,
        Long siteId,
        String processKey,
        String processName,
        BigDecimal fromProgress,
        BigDecimal toProgress,
        Long authorId,
        String authorName,
        ReportReviewStatus reviewStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReportSummaryResponse from(Report report) {
        return new ReportSummaryResponse(
                report.getId(),
                report.getSite().getId(),
                report.getProcessKey(),
                report.getProcessName(),
                report.getFromProgress(),
                report.getToProgress(),
                report.getAuthor().getId(),
                report.getAuthor().getName(),
                report.getReviewStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}
