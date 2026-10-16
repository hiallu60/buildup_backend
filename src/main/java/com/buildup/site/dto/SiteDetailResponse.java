package com.buildup.site.dto;

import com.buildup.site.entity.SiteMember;
import com.buildup.site.entity.SiteMemberRole;
import com.buildup.site.entity.ProgressCalculationMode;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

public record SiteDetailResponse(
        Long id,
        String name,
        String address,
        String workType,
        Long ownerId,
        String ownerName,
        Long companyId,
        String companyName,
        BigDecimal latitude,
        BigDecimal longitude,
        ProgressCalculationMode progressCalculationMode,
        BigDecimal overallProgress,
        SiteMemberRole myRole,
        LocalDateTime createdAt,
        List<SiteProcessResponse> processes
) {

    public static SiteDetailResponse from(
            SiteMember membership,
            List<SiteProcessResponse> processes,
            BigDecimal overallProgress
    ) {
        var site = membership.getSite();
        var company = site.getCompany();

        return new SiteDetailResponse(
                site.getId(),
                site.getName(),
                site.getAddress(),
                site.getWorkType(),
                site.getOwner().getId(),
                site.getOwner().getName(),
                company == null ? null : company.getId(),
                company == null ? null : company.getName(),
                site.getLatitude(),
                site.getLongitude(),
                site.getProgressCalculationMode(),
                overallProgress,
                membership.getMemberRole(),
                site.getCreatedAt(),
                List.copyOf(processes)
        );
    }
}
