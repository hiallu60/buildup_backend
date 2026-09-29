package com.buildup.site.dto;

import com.buildup.site.entity.SiteProcess;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SiteProcessResponse(
        Long id,
        String processKey,
        String name,
        BigDecimal weight,
        BigDecimal progress,
        LocalDateTime lastReportedAt,
        LocalDate planStart,
        LocalDate planEnd,
        Integer sortOrder
) {

    public static SiteProcessResponse from(SiteProcess process) {
        return new SiteProcessResponse(
                process.getId(),
                process.getProcessKey(),
                process.getName(),
                process.getWeight(),
                process.getProgress(),
                process.getLastReportedAt(),
                process.getPlanStart(),
                process.getPlanEnd(),
                process.getSortOrder()
        );
    }
}
