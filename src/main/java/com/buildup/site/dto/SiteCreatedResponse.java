package com.buildup.site.dto;

import com.buildup.site.entity.Site;

public record SiteCreatedResponse(
        Long id,
        String name,
        String address,
        Long companyId,
        String companyName,
        String joinCode
) {

    public static SiteCreatedResponse from(Site site) {
        var company = site.getCompany();
        return new SiteCreatedResponse(
                site.getId(),
                site.getName(),
                site.getAddress(),
                company == null ? null : company.getId(),
                company == null ? null : company.getName(),
                site.getCode()
        );
    }
}
