package com.buildup.material.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record MaterialRequestPageResponse(
        List<MaterialRequestSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static MaterialRequestPageResponse from(Page<MaterialRequestSummaryResponse> result) {
        return new MaterialRequestPageResponse(
                List.copyOf(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
