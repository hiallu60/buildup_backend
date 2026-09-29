package com.buildup.material.dto;

import com.buildup.material.entity.MaterialRequest;
import com.buildup.material.entity.MaterialRequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MaterialRequestSummaryResponse(
        Long id,
        Long siteId,
        String processKey,
        String processName,
        LocalDate neededBy,
        boolean urgent,
        MaterialRequestStatus status,
        Long authorId,
        String authorName,
        LocalDateTime createdAt
) {
    public static MaterialRequestSummaryResponse from(MaterialRequest request) {
        return new MaterialRequestSummaryResponse(
                request.getId(),
                request.getSite().getId(),
                request.getProcessKey(),
                request.getProcessName(),
                request.getNeededBy(),
                request.isUrgent(),
                request.getStatus(),
                request.getAuthor().getId(),
                request.getAuthor().getName(),
                request.getCreatedAt()
        );
    }
}
