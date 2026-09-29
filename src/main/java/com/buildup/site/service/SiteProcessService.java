package com.buildup.site.service;

import com.buildup.common.exception.ApiException;
import com.buildup.site.dto.CreateSiteProcessRequest;
import com.buildup.site.dto.SiteProcessResponse;
import com.buildup.site.dto.UpdateSiteProcessRequest;
import com.buildup.site.entity.MembershipStatus;
import com.buildup.site.entity.Site;
import com.buildup.site.entity.SiteProcess;
import com.buildup.site.repository.SiteMemberRepository;
import com.buildup.site.repository.SiteProcessRepository;
import com.buildup.site.repository.SiteRepository;
import com.buildup.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class SiteProcessService {

    private final SiteRepository siteRepository;
    private final SiteMemberRepository siteMemberRepository;
    private final SiteProcessRepository siteProcessRepository;
    private final SiteAccessService siteAccessService;

    public SiteProcessService(
            SiteRepository siteRepository,
            SiteMemberRepository siteMemberRepository,
            SiteProcessRepository siteProcessRepository,
            SiteAccessService siteAccessService
    ) {
        this.siteRepository = siteRepository;
        this.siteMemberRepository = siteMemberRepository;
        this.siteProcessRepository = siteProcessRepository;
        this.siteAccessService = siteAccessService;
    }

    @Transactional(readOnly = true)
    public List<SiteProcessResponse> list(User user, Long siteId) {
        requireAccessibleSite(user, siteId);
        return siteProcessRepository
                .findAllBySiteIdOrderBySortOrderAscIdAsc(siteId)
                .stream()
                .map(SiteProcessResponse::from)
                .toList();
    }

    @Transactional
    public SiteProcessResponse create(
            User user,
            Long siteId,
            CreateSiteProcessRequest request
    ) {
        Site site = requireManageableSite(user, siteId);
        validatePlanPeriod(request.planStart(), request.planEnd());

        String processKey = request.processKey().trim();
        if (siteProcessRepository.existsBySiteIdAndProcessKey(siteId, processKey)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROCESS_KEY_ALREADY_EXISTS",
                    "이미 사용 중인 공정 키입니다."
            );
        }

        SiteProcess process = siteProcessRepository.save(SiteProcess.create(
                site,
                processKey,
                request.name().trim(),
                request.weight(),
                request.sortOrder()
        ));
        process.updateDefinition(
                request.name().trim(),
                request.weight(),
                request.planStart(),
                request.planEnd(),
                request.sortOrder()
        );
        return SiteProcessResponse.from(process);
    }

    @Transactional
    public SiteProcessResponse update(
            User user,
            Long siteId,
            Long processId,
            UpdateSiteProcessRequest request
    ) {
        requireManageableSite(user, siteId);
        validatePlanPeriod(request.planStart(), request.planEnd());

        SiteProcess process = siteProcessRepository.findByIdAndSiteId(processId, siteId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SITE_PROCESS_NOT_FOUND",
                        "현장 공정을 찾을 수 없습니다."
                ));
        process.updateDefinition(
                request.name().trim(),
                request.weight(),
                request.planStart(),
                request.planEnd(),
                request.sortOrder()
        );
        return SiteProcessResponse.from(process);
    }

    private Site requireAccessibleSite(User user, Long siteId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> siteNotFound());
        if (user.isSystemAdmin()) {
            return site;
        }

        boolean activeMember = siteMemberRepository
                .findBySiteIdAndUserIdAndStatus(siteId, user.getId(), MembershipStatus.ACTIVE)
                .isPresent();
        if (!activeMember) {
            throw siteNotFound();
        }
        return site;
    }

    private Site requireManageableSite(User user, Long siteId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> siteNotFound());
        if (!siteAccessService.canManageSite(user, site)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "SITE_MANAGEMENT_FORBIDDEN",
                    "현장 공정을 관리할 권한이 없습니다."
            );
        }
        return site;
    }

    private void validatePlanPeriod(LocalDate planStart, LocalDate planEnd) {
        if (planStart != null && planEnd != null && planEnd.isBefore(planStart)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PROCESS_PLAN_PERIOD",
                    "공정 종료 예정일은 시작 예정일보다 빠를 수 없습니다."
            );
        }
    }

    private ApiException siteNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "SITE_NOT_FOUND",
                "현장을 찾을 수 없습니다."
        );
    }
}
