package com.buildup.site.service;

import com.buildup.company.entity.Company;
import com.buildup.company.entity.CompanyMembershipRole;
import com.buildup.company.repository.CompanyMembershipRepository;
import com.buildup.site.entity.MembershipStatus;
import com.buildup.site.entity.Site;
import com.buildup.site.entity.SiteMember;
import com.buildup.site.entity.SiteMemberRole;
import com.buildup.site.repository.SiteMemberRepository;
import com.buildup.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteAccessServiceTest {

    @Mock
    private SiteMemberRepository siteMemberRepository;

    @Mock
    private CompanyMembershipRepository companyMembershipRepository;

    @Mock
    private User user;

    @Mock
    private User owner;

    @Mock
    private Site site;

    @Mock
    private Company company;

    @Mock
    private SiteMember siteMember;

    private SiteAccessService siteAccessService;

    @BeforeEach
    void setUp() {
        siteAccessService = new SiteAccessService(
                siteMemberRepository,
                companyMembershipRepository
        );
        when(user.isSystemAdmin()).thenReturn(false);
        when(user.getId()).thenReturn(10L);
        when(owner.getId()).thenReturn(20L);
        when(site.getId()).thenReturn(30L);
        when(site.getOwner()).thenReturn(owner);
        when(site.getCompany()).thenReturn(company);
    }

    @Test
    void hqManagerMustAlsoBeActiveSiteMemberToReviewReports() {
        when(siteMemberRepository.findBySiteIdAndUserIdAndStatus(
                30L,
                10L,
                MembershipStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertThat(siteAccessService.canReviewReports(user, site)).isFalse();
    }

    @Test
    void activeSiteMemberWithHqManagerRoleCanReviewAndManageMaterials() {
        when(siteMember.getMemberRole()).thenReturn(SiteMemberRole.VIEWER);
        when(company.getId()).thenReturn(40L);
        when(siteMemberRepository.findBySiteIdAndUserIdAndStatus(
                30L,
                10L,
                MembershipStatus.ACTIVE
        )).thenReturn(Optional.of(siteMember));
        when(companyMembershipRepository.existsByCompanyIdAndUserIdAndRoleAndStatus(
                40L,
                10L,
                CompanyMembershipRole.HQ_MANAGER,
                MembershipStatus.ACTIVE
        )).thenReturn(true);

        assertThat(siteAccessService.canReviewReports(user, site)).isTrue();
        assertThat(siteAccessService.canManageMaterialRequests(user, site)).isTrue();
    }
}
