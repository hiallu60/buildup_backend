package com.buildup.report.controller;

import com.buildup.report.dto.ApproveReportRequest;
import com.buildup.report.dto.RejectReportRequest;
import com.buildup.report.dto.ReportReviewResponse;
import com.buildup.report.service.ReportReviewService;
import com.buildup.user.entity.User;
import com.buildup.user.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/{reportId}")
public class ReportReviewController {

    private final CurrentUserService currentUserService;
    private final ReportReviewService reportReviewService;

    public ReportReviewController(
            CurrentUserService currentUserService,
            ReportReviewService reportReviewService
    ) {
        this.currentUserService = currentUserService;
        this.reportReviewService = reportReviewService;
    }

    @PostMapping("/approve")
    public ReportReviewResponse approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reportId,
            @Valid @RequestBody ApproveReportRequest request
    ) {
        User reviewer = currentUserService.require(jwt);
        return reportReviewService.approve(reviewer, reportId, request.comment());
    }

    @PostMapping("/reject")
    public ReportReviewResponse reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reportId,
            @Valid @RequestBody RejectReportRequest request
    ) {
        User reviewer = currentUserService.require(jwt);
        return reportReviewService.reject(reviewer, reportId, request.comment());
    }
}
