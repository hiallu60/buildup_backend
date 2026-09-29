package com.buildup.report.controller;

import com.buildup.report.dto.CreateReportRequest;
import com.buildup.report.dto.ReportDetailResponse;
import com.buildup.report.dto.ReportPageResponse;
import com.buildup.report.entity.ReportReviewStatus;
import com.buildup.report.service.ReportService;
import com.buildup.user.entity.User;
import com.buildup.user.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class ReportController {

    private final CurrentUserService currentUserService;
    private final ReportService reportService;

    public ReportController(
            CurrentUserService currentUserService,
            ReportService reportService
    ) {
        this.currentUserService = currentUserService;
        this.reportService = reportService;
    }

    @PostMapping("/api/sites/{siteId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportDetailResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId,
            @Valid @RequestBody CreateReportRequest request
    ) {
        return reportService.create(currentUserService.require(jwt), siteId, request);
    }

    @GetMapping("/api/sites/{siteId}/reports")
    public ReportPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId,
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) ReportReviewStatus reviewStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        User user = currentUserService.require(jwt);
        return reportService.list(user, siteId, processKey, reviewStatus, page, size);
    }

    @GetMapping("/api/reports/{reportId}")
    public ReportDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reportId
    ) {
        return reportService.detail(currentUserService.require(jwt), reportId);
    }
}
