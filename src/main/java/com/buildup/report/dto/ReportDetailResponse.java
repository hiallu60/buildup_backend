package com.buildup.report.dto;

import com.buildup.report.entity.Report;
import com.buildup.report.entity.ReportReviewStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReportDetailResponse(
        Long id,
        Long siteId,
        String siteName,
        String processKey,
        String processName,
        String work,
        BigDecimal fromProgress,
        BigDecimal toProgress,
        String memo,
        String weather,
        Integer workers,
        String equipment,
        BigDecimal submitLatitude,
        BigDecimal submitLongitude,
        BigDecimal submitAccuracy,
        Long authorId,
        String authorName,
        ReportReviewStatus reviewStatus,
        List<String> photoFileNames,
        List<ReportPhotoResponse> photos,
        List<ReportFileResponse> files,
        List<ReportCheckResponse> checks,
        List<ReportReviewEventResponse> reviewEvents,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReportDetailResponse from(
            Report report,
            List<ReportReviewEventResponse> reviewEvents
    ) {
        return new ReportDetailResponse(
                report.getId(),
                report.getSite().getId(),
                report.getSite().getName(),
                report.getProcessKey(),
                report.getProcessName(),
                report.getWork(),
                report.getFromProgress(),
                report.getToProgress(),
                report.getMemo(),
                report.getWeather(),
                report.getWorkers(),
                report.getEquipment(),
                report.getSubmitLatitude(),
                report.getSubmitLongitude(),
                report.getSubmitAccuracy(),
                report.getAuthor().getId(),
                report.getAuthor().getName(),
                report.getReviewStatus(),
                report.getPhotos().stream().map(photo -> photo.getOriginalName()).toList(),
                report.getPhotos().stream().map(ReportPhotoResponse::from).toList(),
                report.getFiles().stream().map(ReportFileResponse::from).toList(),
                report.getChecks().stream().map(ReportCheckResponse::from).toList(),
                List.copyOf(reviewEvents),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}
