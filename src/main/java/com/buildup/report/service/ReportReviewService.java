package com.buildup.report.service;

import com.buildup.common.exception.ApiException;
import com.buildup.notification.service.NotificationService;
import com.buildup.report.dto.ReportReviewResponse;
import com.buildup.report.entity.Report;
import com.buildup.report.entity.ReportReviewEvent;
import com.buildup.report.repository.ReportRepository;
import com.buildup.report.repository.ReportReviewEventRepository;
import com.buildup.site.entity.SiteProcess;
import com.buildup.site.repository.SiteProcessRepository;
import com.buildup.site.service.SiteAccessService;
import com.buildup.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ReportReviewService {

    private final ReportRepository reportRepository;
    private final ReportReviewEventRepository reviewEventRepository;
    private final SiteProcessRepository siteProcessRepository;
    private final SiteAccessService siteAccessService;
    private final NotificationService notificationService;

    public ReportReviewService(
            ReportRepository reportRepository,
            ReportReviewEventRepository reviewEventRepository,
            SiteProcessRepository siteProcessRepository,
            SiteAccessService siteAccessService,
            NotificationService notificationService
    ) {
        this.reportRepository = reportRepository;
        this.reviewEventRepository = reviewEventRepository;
        this.siteProcessRepository = siteProcessRepository;
        this.siteAccessService = siteAccessService;
        this.notificationService = notificationService;
    }

    @Transactional
    public ReportReviewResponse approve(User reviewer, Long reportId, String comment) {
        Report report = pendingReport(reviewer, reportId);
        SiteProcess process = siteProcessRepository
                .findBySiteIdAndProcessKey(report.getSite().getId(), report.getProcessKey())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "REPORT_PROCESS_NOT_FOUND",
                        "보고서의 대상 공정이 더 이상 존재하지 않습니다."
                ));

        if (process.getProgress().compareTo(report.getFromProgress()) != 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "STALE_REPORT_PROGRESS",
                    "보고서 작성 후 공정 진행률이 변경되었습니다."
            );
        }

        LocalDateTime now = LocalDateTime.now();
        process.applyApprovedProgress(report.getToProgress(), now);
        report.approve();
        reviewEventRepository.save(ReportReviewEvent.approved(report, reviewer, comment));
        notificationService.create(
                report.getAuthor(),
                report.getSite(),
                "REPORT_APPROVED",
                "보고서가 승인되었습니다",
                report.getSite().getName() + " · " + report.getProcessName(),
                "REPORT",
                report.getId()
        );
        return ReportReviewResponse.from(report);
    }

    @Transactional
    public ReportReviewResponse reject(User reviewer, Long reportId, String comment) {
        Report report = pendingReport(reviewer, reportId);
        report.reject();
        reviewEventRepository.save(ReportReviewEvent.rejected(report, reviewer, comment));
        notificationService.create(
                report.getAuthor(),
                report.getSite(),
                "REPORT_REJECTED",
                "보고서가 반려되었습니다",
                comment.trim(),
                "REPORT",
                report.getId()
        );
        return ReportReviewResponse.from(report);
    }

    private Report pendingReport(User reviewer, Long reportId) {
        Report report = reportRepository.findForReview(reportId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "REPORT_NOT_FOUND",
                        "보고서를 찾을 수 없습니다."
                ));

        if (!siteAccessService.canReviewReports(reviewer, report.getSite())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "REPORT_REVIEW_FORBIDDEN",
                    "보고서를 검토할 권한이 없습니다."
            );
        }
        if (!report.isPendingReview()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REPORT_ALREADY_REVIEWED",
                    "이미 검토가 완료된 보고서입니다."
            );
        }
        return report;
    }
}
