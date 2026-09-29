package com.buildup.site.controller;

import com.buildup.site.dto.ChangeSiteMemberRoleRequest;
import com.buildup.site.dto.CreateSiteRequest;
import com.buildup.site.dto.CreateSiteProcessRequest;
import com.buildup.site.dto.JoinSiteRequest;
import com.buildup.site.dto.SiteCreatedResponse;
import com.buildup.site.dto.SiteDetailResponse;
import com.buildup.site.dto.SiteJoinCodeResponse;
import com.buildup.site.dto.SiteMemberResponse;
import com.buildup.site.dto.SiteSummaryResponse;
import com.buildup.site.dto.SiteProcessResponse;
import com.buildup.site.dto.UpdateSiteProcessRequest;
import com.buildup.site.service.SiteManagementService;
import com.buildup.site.service.SiteProcessService;
import com.buildup.site.service.SiteQueryService;
import com.buildup.user.entity.User;
import com.buildup.user.service.CurrentUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/sites")
public class SiteController {

    private final CurrentUserService currentUserService;
    private final SiteQueryService siteQueryService;
    private final SiteManagementService siteManagementService;
    private final SiteProcessService siteProcessService;

    public SiteController(
            CurrentUserService currentUserService,
            SiteQueryService siteQueryService,
            SiteManagementService siteManagementService,
            SiteProcessService siteProcessService
    ) {
        this.currentUserService = currentUserService;
        this.siteQueryService = siteQueryService;
        this.siteManagementService = siteManagementService;
        this.siteProcessService = siteProcessService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SiteCreatedResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSiteRequest request
    ) {
        return siteManagementService.create(currentUserService.require(jwt), request);
    }

    @PostMapping("/join")
    public SiteSummaryResponse join(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody JoinSiteRequest request
    ) {
        return siteManagementService.join(currentUserService.require(jwt), request.code());
    }

    @GetMapping
    public List<SiteSummaryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        User user = currentUserService.require(jwt);
        return siteQueryService.list(user);
    }

    @GetMapping("/{siteId}")
    public SiteDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId
    ) {
        User user = currentUserService.require(jwt);
        return siteQueryService.detail(user, siteId);
    }

    @GetMapping("/{siteId}/members")
    public List<SiteMemberResponse> members(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId
    ) {
        return siteManagementService.members(currentUserService.require(jwt), siteId);
    }

    @GetMapping("/{siteId}/join-code")
    public SiteJoinCodeResponse joinCode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId
    ) {
        return siteManagementService.joinCode(currentUserService.require(jwt), siteId);
    }

    @PatchMapping("/{siteId}/members/{membershipId}/role")
    public SiteMemberResponse changeMemberRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId,
            @PathVariable Long membershipId,
            @Valid @RequestBody ChangeSiteMemberRoleRequest request
    ) {
        return siteManagementService.changeRole(
                currentUserService.require(jwt),
                siteId,
                membershipId,
                request.role()
        );
    }

    @GetMapping("/{siteId}/processes")
    public List<SiteProcessResponse> processes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId
    ) {
        return siteProcessService.list(currentUserService.require(jwt), siteId);
    }

    @PostMapping("/{siteId}/processes")
    @ResponseStatus(HttpStatus.CREATED)
    public SiteProcessResponse createProcess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId,
            @Valid @RequestBody CreateSiteProcessRequest request
    ) {
        return siteProcessService.create(
                currentUserService.require(jwt),
                siteId,
                request
        );
    }

    @PutMapping("/{siteId}/processes/{processId}")
    public SiteProcessResponse updateProcess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId,
            @PathVariable Long processId,
            @Valid @RequestBody UpdateSiteProcessRequest request
    ) {
        return siteProcessService.update(
                currentUserService.require(jwt),
                siteId,
                processId,
                request
        );
    }
}
