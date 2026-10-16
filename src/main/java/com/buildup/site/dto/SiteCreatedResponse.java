package com.buildup.site.dto;

import com.buildup.site.entity.Site;
import com.buildup.site.entity.ProgressCalculationMode;

import java.math.BigDecimal;

public record SiteCreatedResponse(
        Long id,
        String name,
        String address,
        String workType,
        Long companyId,
        String companyName,
        BigDecimal latitude,
        BigDecimal longitude,
        ProgressCalculationMode progressCalculationMode,
        String joinCode
) {

    public static SiteCreatedResponse from(Site site) {
        var company = site.getCompany();
        return new SiteCreatedResponse(
                site.getId(),
                site.getName(),
                site.getAddress(),
                site.getWorkType(),
                company == null ? null : company.getId(),
                company == null ? null : company.getName(),
                site.getLatitude(),
                site.getLongitude(),
                site.getProgressCalculationMode(),
                site.getCode()
        );
    }
}
