package com.buildup.report.service;

import com.buildup.common.exception.ApiException;
import com.buildup.report.dto.CreateReportRequest;
import com.buildup.report.dto.ReportDetailResponse;
import com.buildup.report.dto.ReportFileRequest;
import com.buildup.report.dto.ReportPageResponse;
import com.buildup.report.dto.ReportReviewEventResponse;
import com.buildup.report.dto.ReportSummaryResponse;
import com.buildup.report.entity.Report;
import com.buildup.report.entity.ReportFile;
import com.buildup.report.entity.ReportPhoto;
import com.buildup.report.entity.ReportReviewStatus;
import com.buildup.report.repository.ReportRepository;
import com.buildup.report.repository.ReportReviewEventRepository;
import com.buildup.site.entity.Site;
import com.buildup.site.entity.SiteProcess;
import com.buildup.site.repository.SiteProcessRepository;
import com.buildup.site.repository.SiteRepository;
import com.buildup.site.service.SiteAccessService;
import com.buildup.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportReviewEventRepository reviewEventRepository;
    private final SiteRepository siteRepository;
    private final SiteProcessRepository siteProcessRepository;
    private final SiteAccessService siteAccessService;

    public ReportService(
            ReportRepository reportRepository,
            ReportReviewEventRepository reviewEventRepository,
            SiteRepository siteRepository,
            SiteProcessRepository siteProcessRepository,
            SiteAccessService siteAccessService
    ) {
        this.reportRepository = reportRepository;
        this.reviewEventRepository = reviewEventRepository;
        this.siteRepository = siteRepository;
        this.siteProcessRepository = siteProcessRepository;
        this.siteAccessService = siteAccessService;
    }

    @Transactional
    public ReportDetailResponse create(User author, Long siteId, CreateReportRequest request) {
        Site site = requireSite(siteId);
        if (!siteAccessService.canSubmitReports(author, site)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "REPORT_SUBMIT_FORBIDDEN",
                    "보고서를 작성할 권한이 없습니다."
            );
        }

        String processKey = request.processKey().trim();
        SiteProcess process = siteProcessRepository
                .findBySiteIdAndProcessKey(siteId, processKey)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SITE_PROCESS_NOT_FOUND",
                        "현장 공정을 찾을 수 없습니다."
                ));

        if (reportRepository.existsBySiteIdAndProcessKeyAndReviewStatus(
                siteId,
                processKey,
                ReportReviewStatus.PENDING
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PENDING_REPORT_ALREADY_EXISTS",
                    "해당 공정에는 검토 대기 중인 보고서가 이미 있습니다."
            );
        }

        if (request.toProgress().compareTo(process.getProgress()) <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REPORT_PROGRESS_MUST_INCREASE",
                    "목표 진행률은 현재 진행률보다 커야 합니다."
            );
        }

        List<ReportPhoto> photos = safeList(request.photoFileNames()).stream()
                .map(String::trim)
                .map(ReportPhoto::of)
                .toList();
        List<ReportFile> files = safeList(request.files()).stream()
                .map(this::toReportFile)
                .toList();

        Report report = reportRepository.saveAndFlush(Report.submit(
                site,
                process.getProcessKey(),
                process.getName(),
                process.getProgress(),
                request.toProgress(),
                normalize(request.memo()),
                normalize(request.weather()),
                request.workers(),
                normalize(request.equipment()),
                photos,
                files,
                author
        ));
        return ReportDetailResponse.from(report, List.of());
    }

    @Transactional(readOnly = true)
    public ReportPageResponse list(
            User user,
            Long siteId,
            String processKey,
            ReportReviewStatus reviewStatus,
            int page,
            int size
    ) {
        Site site = requireSite(siteId);
        requireSiteAccess(user, site);

        String normalizedProcessKey = normalize(processKey);
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return ReportPageResponse.from(
                reportRepository.search(siteId, normalizedProcessKey, reviewStatus, pageable)
                        .map(ReportSummaryResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse detail(User user, Long reportId) {
        Report report = reportRepository.findDetailById(reportId)
                .orElseThrow(() -> reportNotFound());
        requireSiteAccess(user, report.getSite());

        List<ReportReviewEventResponse> reviewEvents = reviewEventRepository
                .findAllByReportIdOrderByCreatedAtAscIdAsc(reportId)
                .stream()
                .map(ReportReviewEventResponse::from)
                .toList();
        return ReportDetailResponse.from(report, reviewEvents);
    }

    private Site requireSite(Long siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SITE_NOT_FOUND",
                        "현장을 찾을 수 없습니다."
                ));
    }

    private void requireSiteAccess(User user, Site site) {
        if (!siteAccessService.canAccessSite(user, site)) {
            throw reportNotFound();
        }
    }

    private ReportFile toReportFile(ReportFileRequest file) {
        return ReportFile.of(
                file.path().trim(),
                file.originalName().trim(),
                file.sizeBytes()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private ApiException reportNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "REPORT_NOT_FOUND",
                "보고서를 찾을 수 없습니다."
        );
    }
}
