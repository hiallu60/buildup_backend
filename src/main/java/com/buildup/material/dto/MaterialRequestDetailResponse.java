package com.buildup.material.dto;

import com.buildup.material.entity.MaterialRequest;
import com.buildup.material.entity.MaterialRequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MaterialRequestDetailResponse(
        Long id,
        Long siteId,
        String siteName,
        String processKey,
        String processName,
        LocalDate neededBy,
        boolean urgent,
        String note,
        MaterialRequestStatus status,
        String rejectReason,
        Long authorId,
        String authorName,
        List<MaterialRequestItemResponse> items,
        List<MaterialRequestEventResponse> events,
        LocalDateTime createdAt
) {
    public static MaterialRequestDetailResponse from(MaterialRequest request) {
        return new MaterialRequestDetailResponse(
                request.getId(),
                request.getSite().getId(),
                request.getSite().getName(),
                request.getProcessKey(),
                request.getProcessName(),
                request.getNeededBy(),
                request.isUrgent(),
                request.getNote(),
                request.getStatus(),
                request.getRejectReason(),
                request.getAuthor().getId(),
                request.getAuthor().getName(),
                request.getItems().stream().map(MaterialRequestItemResponse::from).toList(),
                request.getEvents().stream().map(MaterialRequestEventResponse::from).toList(),
                request.getCreatedAt()
        );
    }
}
