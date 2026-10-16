package com.buildup.report.dto;

import com.buildup.report.entity.ReportPhoto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReportPhotoResponse(
        Long id,
        String originalName,
        String contentType,
        Long sizeBytes,
        LocalDateTime takenAt,
        BigDecimal latitude,
        BigDecimal longitude,
        String contentUrl,
        LocalDateTime createdAt
) {
    public static ReportPhotoResponse from(ReportPhoto photo) {
        String contentUrl = photo.isStoredInS3()
                ? "/api/reports/%d/photos/%d/content".formatted(
                        photo.getReport().getId(),
                        photo.getId()
                )
                : null;
        return new ReportPhotoResponse(
                photo.getId(),
                photo.getOriginalName(),
                photo.getContentType(),
                photo.getSizeBytes(),
                photo.getTakenAt(),
                photo.getLatitude(),
                photo.getLongitude(),
                contentUrl,
                photo.getCreatedAt()
        );
    }
}
