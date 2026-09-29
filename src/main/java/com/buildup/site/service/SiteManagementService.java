package com.buildup.site.service;

import com.buildup.common.exception.ApiException;
import com.buildup.company.entity.Company;
import com.buildup.company.repository.CompanyMembershipRepository;
import com.buildup.company.repository.CompanyRepository;
import com.buildup.site.dto.CreateSiteRequest;
import com.buildup.site.dto.SiteCreatedResponse;
import com.buildup.site.dto.SiteJoinCodeResponse;
import com.buildup.site.dto.SiteMemberResponse;
import com.buildup.site.dto.SiteSummaryResponse;
import com.buildup.site.entity.MembershipStatus;
import com.buildup.site.entity.Site;
import com.buildup.site.entity.SiteMember;
import com.buildup.site.entity.SiteMemberRole;
import com.buildup.site.repository.SiteMemberRepository;
import com.buildup.site.repository.SiteRepository;
import com.buildup.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class SiteManagementService {

    private static final char[] CODE_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 10;
    private static final int CODE_GENERATION_ATTEMPTS = 10;

    private final SiteRepository siteRepository;
    private final SiteMemberRepository siteMemberRepository;
    private final CompanyRepository companyRepository;
    private final CompanyMembershipRepository companyMembershipRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public SiteManagementService(
            SiteRepository siteRepository,
            SiteMemberRepository siteMemberRepository,
            CompanyRepository companyRepository,
            CompanyMembershipRepository companyMembershipRepository
    ) {
        this.siteRepository = siteRepository;
        this.siteMemberRepository = siteMemberRepository;
        this.companyRepository = companyRepository;
        this.companyMembershipRepository = companyMembershipRepository;
    }

    @Transactional
    public SiteCreatedResponse create(User owner, CreateSiteRequest request) {
        Company company = resolveCompany(owner, request.companyId());
        Site site = siteRepository.save(Site.create(
                request.name().trim(),
                request.address().trim(),
                owner,
                generateUniqueCode(),
                company
        ));
        siteMemberRepository.save(SiteMember.join(site, owner, SiteMemberRole.OWNER));
        return SiteCreatedResponse.from(site);
    }

    @Transactional
    public SiteSummaryResponse join(User user, String rawCode) {
        String code = rawCode.trim().toUpperCase();
        Site site = siteRepository.findByCode(code)
                .orElseThrow(() -> notFound("JOIN_CODE_NOT_FOUND", "유효하지 않은 현장 코드입니다."));

        SiteMember membership = siteMemberRepository
                .findBySiteIdAndUserId(site.getId(), user.getId())
                .map(this::reactivate)
                .orElseGet(() -> SiteMember.join(site, user, SiteMemberRole.WORKER));

        if (membership.getId() == null) {
            siteMemberRepository.save(membership);
        }
        return SiteSummaryResponse.from(membership);
    }

    @Transactional(readOnly = true)
    public List<SiteMemberResponse> members(User requester, Long siteId) {
        Site site = requireSite(siteId);
        if (!requester.isSystemAdmin()) {
            SiteMember requesterMembership = requireActiveMembership(requester, siteId);
            if (!requesterMembership.getMemberRole().canViewMembers()) {
                throw forbidden("SITE_MEMBER_LIST_FORBIDDEN", "현장 구성원을 조회할 권한이 없습니다.");
            }
        }

        return siteMemberRepository
                .findAllBySiteIdAndStatusOrderByJoinedAtAscIdAsc(site.getId(), MembershipStatus.ACTIVE)
                .stream()
                .map(SiteMemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SiteJoinCodeResponse joinCode(User requester, Long siteId) {
        Site site = requireSite(siteId);
        requireOwner(requester, site);
        return new SiteJoinCodeResponse(site.getCode());
    }

    @Transactional
    public SiteMemberResponse changeRole(
            User requester,
            Long siteId,
            Long membershipId,
            SiteMemberRole newRole
    ) {
        Site site = requireSite(siteId);
        requireOwner(requester, site);

        if (newRole == SiteMemberRole.OWNER) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OWNER_ROLE_CHANGE_NOT_SUPPORTED",
                    "소유자 역할은 이 API로 변경할 수 없습니다."
            );
        }

        SiteMember target = siteMemberRepository.findById(membershipId)
                .filter(member -> member.getSite().getId().equals(siteId))
                .filter(SiteMember::isActive)
                .orElseThrow(() -> notFound("SITE_MEMBER_NOT_FOUND", "현장 구성원을 찾을 수 없습니다."));

        if (target.getMemberRole() == SiteMemberRole.OWNER) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OWNER_ROLE_CHANGE_NOT_SUPPORTED",
                    "소유자 역할은 이 API로 변경할 수 없습니다."
            );
        }

        target.changeRole(newRole);
        return SiteMemberResponse.from(target);
    }

    private Company resolveCompany(User owner, Long companyId) {
        if (companyId == null) {
            return null;
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> notFound("COMPANY_NOT_FOUND", "회사를 찾을 수 없습니다."));

        boolean member = companyMembershipRepository.existsByCompanyIdAndUserIdAndStatus(
                companyId,
                owner.getId(),
                MembershipStatus.ACTIVE
        );
        if (!owner.isSystemAdmin() && !member) {
            throw forbidden("COMPANY_ACCESS_DENIED", "해당 회사에 현장을 생성할 권한이 없습니다.");
        }
        return company;
    }

    private SiteMember reactivate(SiteMember membership) {
        if (membership.isActive()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ALREADY_SITE_MEMBER",
                    "이미 참여 중인 현장입니다."
            );
        }
        boolean siteOwner = membership.getSite().getOwner().getId()
                .equals(membership.getUser().getId());
        membership.activateAs(siteOwner ? SiteMemberRole.OWNER : SiteMemberRole.WORKER);
        return membership;
    }

    private Site requireSite(Long siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다."));
    }

    private SiteMember requireActiveMembership(User user, Long siteId) {
        return siteMemberRepository
                .findBySiteIdAndUserIdAndStatus(siteId, user.getId(), MembershipStatus.ACTIVE)
                .orElseThrow(() -> notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다."));
    }

    private void requireOwner(User requester, Site site) {
        if (requester.isSystemAdmin() || site.getOwner().getId().equals(requester.getId())) {
            return;
        }
        throw forbidden("SITE_OWNER_REQUIRED", "현장 소유자 권한이 필요합니다.");
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                code.append(CODE_CHARACTERS[secureRandom.nextInt(CODE_CHARACTERS.length)]);
            }
            String generated = code.toString();
            if (!siteRepository.existsByCode(generated)) {
                return generated;
            }
        }
        throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SITE_CODE_GENERATION_FAILED",
                "현장 코드를 생성하지 못했습니다. 잠시 후 다시 시도해주세요."
        );
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }
}
