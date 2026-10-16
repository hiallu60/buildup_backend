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
import com.buildup.site.entity.ProgressCalculationMode;
import com.buildup.site.entity.SiteProcess;
import com.buildup.site.repository.SiteProcessRepository;
import com.buildup.site.repository.SiteRepository;
import com.buildup.site.service.SiteAccessService;
import com.buildup.site.service.SiteProgressService;
import com.buildup.storage.service.S3PhotoStorageService;
import com.buildup.storage.service.S3ReportFileStorageService;
import com.buildup.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final ReportReviewEventRepository reviewEventRepository;
    private final SiteRepository siteRepository;
    private final SiteProcessRepository siteProcessRepository;
    private final SiteAccessService siteAccessService;
    private final S3PhotoStorageService photoStorageService;
    private final S3ReportFileStorageService fileStorageService;
    private final ReportCheckService reportCheckService;
    private final SiteProgressService siteProgressService;

    public ReportService(
            ReportRepository reportRepository,
            ReportReviewEventRepository reviewEventRepository,
            SiteRepository siteRepository,
            SiteProcessRepository siteProcessRepository,
            SiteAccessService siteAccessService,
            S3PhotoStorageService photoStorageService,
            S3ReportFileStorageService fileStorageService,
            ReportCheckService reportCheckService,
            SiteProgressService siteProgressService
    ) {
        this.reportRepository = reportRepository;
        this.reviewEventRepository = reviewEventRepository;
        this.siteRepository = siteRepository;
        this.siteProcessRepository = siteProcessRepository;
        this.siteAccessService = siteAccessService;
        this.photoStorageService = photoStorageService;
        this.fileStorageService = fileStorageService;
        this.reportCheckService = reportCheckService;
        this.siteProgressService = siteProgressService;
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

        String processKey = normalize(request.processKey());
        validateProgressMode(site, processKey);
        SiteProcess process = processKey == null
                ? null
                : siteProcessRepository.findBySiteIdAndProcessKey(siteId, processKey)
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.NOT_FOUND,
                                "SITE_PROCESS_NOT_FOUND",
                                "현장 공정을 찾을 수 없습니다."
                        ));

        boolean pendingReportExists = processKey == null
                ? reportRepository.existsBySiteIdAndProcessKeyIsNullAndReviewStatus(
                        siteId,
                        ReportReviewStatus.PENDING
                )
                : reportRepository.existsBySiteIdAndProcessKeyAndReviewStatus(
                        siteId,
                        processKey,
                        ReportReviewStatus.PENDING
                );
        if (pendingReportExists) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PENDING_REPORT_ALREADY_EXISTS",
                    processKey == null
                            ? "현장에는 검토 대기 중인 보고서가 이미 있습니다."
                            : "해당 공정에는 검토 대기 중인 보고서가 이미 있습니다."
            );
        }

        if (site.getProgressCalculationMode() == ProgressCalculationMode.PROCESS_WEIGHTED) {
            siteProgressService.validateCompleteWeights(site);
        }
        BigDecimal currentProgress = process == null
                ? siteProgressService.currentProgress(site)
                : process.getProgress();
        if (request.toProgress().compareTo(currentProgress) <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REPORT_PROGRESS_MUST_INCREASE",
                    "목표 진행률은 현재 진행률보다 커야 합니다."
            );
        }

        List<ReportPhoto> photos = safeList(request.photoFileNames()).stream()
                .map(String::trim)
                .map(ReportPhoto::legacy)
                .toList();
        List<ReportFile> files = safeList(request.files()).stream()
                .map(this::toReportFile)
                .toList();

        validateLocation(
                request.submitLatitude(),
                request.submitLongitude(),
                request.submitAccuracy()
        );
        String work = request.work().trim();
        Report report = Report.submit(
                site,
                process == null ? null : process.getProcessKey(),
                process == null ? null : process.getName(),
                work,
                currentProgress,
                request.toProgress(),
                normalize(request.memo()),
                normalize(request.weather()),
                request.workers(),
                normalize(request.equipment()),
                request.submitLatitude(),
                request.submitLongitude(),
                request.submitAccuracy(),
                photos,
                files,
                author
        );
        reportCheckService.addSubmissionChecks(report);
        LocalDateTime checkedAt = LocalDateTime.now();
        report.getPhotos().forEach(photo -> reportCheckService.addPhotoChecks(
                report,
                photo,
                checkedAt
        ));
        reportRepository.saveAndFlush(report);
        log.info(
                "event=report.created reportId={} siteId={} authorId={}",
                report.getId(),
                site.getId(),
                author.getId()
        );
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

    @Transactional
    public void delete(User user, Long reportId) {
        Report report = reportRepository.findForPhotoUpdate(reportId)
                .orElseThrow(this::reportNotFound);
        boolean author = report.getAuthor().getId().equals(user.getId());
        boolean manager = siteAccessService.canManageSite(user, report.getSite());
        if (!author && !manager && !user.isSystemAdmin()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "REPORT_DELETE_FORBIDDEN",
                    "보고서를 삭제할 권한이 없습니다."
            );
        }
        if (!report.isPendingReview()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REPORT_DELETE_NOT_ALLOWED",
                    "검토가 끝난 보고서는 삭제할 수 없습니다."
            );
        }

        List<String> photoS3Keys = report.getPhotos().stream()
                .map(ReportPhoto::getS3Key)
                .filter(key -> key != null && !key.isBlank())
                .toList();
        List<String> fileS3Keys = report.getFiles().stream()
                .map(ReportFile::getS3Key)
                .filter(key -> key != null && !key.isBlank())
                .toList();
        reportRepository.delete(report);
        reportRepository.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                photoS3Keys.forEach(key -> photoStorageService.deleteQuietly(reportId, key));
                fileS3Keys.forEach(key -> fileStorageService.deleteQuietly(reportId, key));
            }
        });
        log.info(
                "event=report.deleted reportId={} siteId={} actorId={} photoCount={} fileCount={}",
                reportId,
                report.getSite().getId(),
                user.getId(),
                photoS3Keys.size(),
                fileS3Keys.size()
        );
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
        return ReportFile.legacy(
                file.path().trim(),
                file.originalName().trim(),
                file.sizeBytes()
        );
    }

    private void validateLocation(
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracy
    ) {
        if ((latitude == null) != (longitude == null)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REPORT_LOCATION_INCOMPLETE",
                    "보고서 제출 위치는 위도와 경도를 함께 입력해야 합니다."
            );
        }
        if (latitude != null
                && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_LATITUDE_INVALID", "제출 위도가 올바르지 않습니다.");
        }
        if (longitude != null
                && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_LONGITUDE_INVALID", "제출 경도가 올바르지 않습니다.");
        }
        if (accuracy != null && accuracy.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_ACCURACY_INVALID", "위치 정확도는 음수일 수 없습니다.");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateProgressMode(Site site, String processKey) {
        if (site.getProgressCalculationMode() == ProgressCalculationMode.PROCESS_WEIGHTED
                && processKey == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REPORT_PROCESS_REQUIRED",
                    "공정별 진행률 현장에서는 보고서에 공정 키가 필요합니다."
            );
        }
        if (site.getProgressCalculationMode() == ProgressCalculationMode.MANUAL_INPUT
                && processKey != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REPORT_PROCESS_NOT_ALLOWED",
                    "직접 입력 방식의 현장에서는 보고서에 공정 키를 사용할 수 없습니다."
            );
        }
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
