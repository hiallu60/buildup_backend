package com.buildup.site.dto;

import com.buildup.site.entity.SiteMember;
import com.buildup.site.entity.SiteMemberRole;

import java.time.LocalDateTime;
import java.util.List;

public record SiteDetailResponse(
        Long id,
        String name,
        String address,
        Long ownerId,
        String ownerName,
        Long companyId,
        String companyName,
        SiteMemberRole myRole,
        LocalDateTime createdAt,
        List<SiteProcessResponse> processes
) {

    public static SiteDetailResponse from(
            SiteMember membership,
            List<SiteProcessResponse> processes
    ) {
        var site = membership.getSite();
        var company = site.getCompany();

        return new SiteDetailResponse(
                site.getId(),
                site.getName(),
                site.getAddress(),
                site.getOwner().getId(),
                site.getOwner().getName(),
                company == null ? null : company.getId(),
                company == null ? null : company.getName(),
                membership.getMemberRole(),
                site.getCreatedAt(),
                List.copyOf(processes)
        );
    }
}
