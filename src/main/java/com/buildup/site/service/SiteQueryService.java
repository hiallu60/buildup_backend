package com.buildup.site.service;

import com.buildup.common.exception.ApiException;
import com.buildup.site.dto.SiteDetailResponse;
import com.buildup.site.dto.SiteProcessResponse;
import com.buildup.site.dto.SiteSummaryResponse;
import com.buildup.site.entity.MembershipStatus;
import com.buildup.site.entity.SiteMember;
import com.buildup.site.repository.SiteMemberRepository;
import com.buildup.site.repository.SiteProcessRepository;
import com.buildup.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SiteQueryService {

    private final SiteMemberRepository siteMemberRepository;
    private final SiteProcessRepository siteProcessRepository;
    private final SiteProgressService siteProgressService;

    public SiteQueryService(
            SiteMemberRepository siteMemberRepository,
            SiteProcessRepository siteProcessRepository,
            SiteProgressService siteProgressService
    ) {
        this.siteMemberRepository = siteMemberRepository;
        this.siteProcessRepository = siteProcessRepository;
        this.siteProgressService = siteProgressService;
    }

    @Transactional(readOnly = true)
    public List<SiteSummaryResponse> list(User user) {
        return siteMemberRepository
                .findAllAccessibleSites(user.getId(), MembershipStatus.ACTIVE)
                .stream()
                .map(membership -> SiteSummaryResponse.from(
                        membership,
                        siteProgressService.currentProgress(membership.getSite())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public SiteDetailResponse detail(User user, Long siteId) {
        SiteMember membership = siteMemberRepository
                .findBySiteIdAndUserIdAndStatus(
                        siteId,
                        user.getId(),
                        MembershipStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SITE_NOT_FOUND",
                        "현장을 찾을 수 없습니다."
                ));

        List<SiteProcessResponse> processes = siteProcessRepository
                .findAllBySiteIdOrderBySortOrderAscIdAsc(siteId)
                .stream()
                .map(SiteProcessResponse::from)
                .toList();

        return SiteDetailResponse.from(
                membership,
                processes,
                siteProgressService.currentProgress(membership.getSite())
        );
    }
}
