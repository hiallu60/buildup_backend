package com.buildup.report.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ReportPageResponse(
        List<ReportSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static ReportPageResponse from(Page<ReportSummaryResponse> result) {
        return new ReportPageResponse(
                List.copyOf(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
