package com.buildup.report.dto;

import com.buildup.report.entity.ReportFile;

import java.time.LocalDateTime;

public record ReportFileResponse(
        Long id,
        String path,
        String originalName,
        String contentType,
        Long sizeBytes,
        String contentUrl,
        LocalDateTime createdAt
) {
    public static ReportFileResponse from(ReportFile file) {
        String contentUrl = file.isStoredInS3()
                ? "/api/reports/%d/files/%d/content".formatted(
                        file.getReport().getId(),
                        file.getId()
                )
                : null;
        return new ReportFileResponse(
                file.getId(),
                file.getLegacyPath(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                contentUrl,
                file.getCreatedAt()
        );
    }
}
