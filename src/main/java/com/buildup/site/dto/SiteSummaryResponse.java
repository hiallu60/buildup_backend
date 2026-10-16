package com.buildup.site.dto;

import com.buildup.site.entity.SiteMember;
import com.buildup.site.entity.SiteMemberRole;
import com.buildup.site.entity.ProgressCalculationMode;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public record SiteSummaryResponse(
        Long id,
        String name,
        String address,
        String workType,
        Long companyId,
        String companyName,
        BigDecimal latitude,
        BigDecimal longitude,
        ProgressCalculationMode progressCalculationMode,
        BigDecimal overallProgress,
        SiteMemberRole myRole,
        LocalDateTime createdAt
) {

    public static SiteSummaryResponse from(SiteMember membership, BigDecimal overallProgress) {
        var site = membership.getSite();
        var company = site.getCompany();

        return new SiteSummaryResponse(
                site.getId(),
                site.getName(),
                site.getAddress(),
                site.getWorkType(),
                company == null ? null : company.getId(),
                company == null ? null : company.getName(),
                site.getLatitude(),
                site.getLongitude(),
                site.getProgressCalculationMode(),
                overallProgress,
                membership.getMemberRole(),
                site.getCreatedAt()
        );
    }
}
