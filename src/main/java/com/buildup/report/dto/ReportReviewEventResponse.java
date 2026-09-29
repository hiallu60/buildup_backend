package com.buildup.report.dto;

import com.buildup.report.entity.ReportReviewEvent;
import com.buildup.report.entity.ReportReviewStatus;

import java.time.LocalDateTime;

public record ReportReviewEventResponse(
        Long id,
        Long reviewerId,
        String reviewerName,
        ReportReviewStatus status,
        String comment,
        LocalDateTime createdAt
) {
    public static ReportReviewEventResponse from(ReportReviewEvent event) {
        return new ReportReviewEventResponse(
                event.getId(),
                event.getReviewer().getId(),
                event.getReviewer().getName(),
                event.getStatus(),
                event.getComment(),
                event.getCreatedAt()
        );
    }
}
