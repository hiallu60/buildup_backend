package com.buildup.report.dto;

import com.buildup.report.entity.ReportCheck;

import java.time.LocalDateTime;
import java.util.Locale;

public record ReportCheckResponse(
        Long id,
        Long photoId,
        String type,
        String message,
        LocalDateTime createdAt
) {
    public static ReportCheckResponse from(ReportCheck check) {
        return new ReportCheckResponse(
                check.getId(),
                check.getPhoto() == null ? null : check.getPhoto().getId(),
                check.getType().name().toLowerCase(Locale.ROOT),
                check.getMessage(),
                check.getCreatedAt()
        );
    }
}
