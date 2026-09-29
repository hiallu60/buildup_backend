package com.buildup.site.dto;

import com.buildup.site.entity.SiteMember;
import com.buildup.site.entity.SiteMemberRole;

import java.time.LocalDateTime;

public record SiteSummaryResponse(
        Long id,
        String name,
        String address,
        Long companyId,
        String companyName,
        SiteMemberRole myRole,
        LocalDateTime createdAt
) {

    public static SiteSummaryResponse from(SiteMember membership) {
        var site = membership.getSite();
        var company = site.getCompany();

        return new SiteSummaryResponse(
                site.getId(),
                site.getName(),
                site.getAddress(),
                company == null ? null : company.getId(),
                company == null ? null : company.getName(),
                membership.getMemberRole(),
                site.getCreatedAt()
        );
    }
}
