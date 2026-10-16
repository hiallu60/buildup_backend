package com.buildup.site.service;

import com.buildup.common.exception.ApiException;
import com.buildup.report.entity.Report;
import com.buildup.report.entity.ReportReviewStatus;
import com.buildup.report.repository.ReportRepository;
import com.buildup.site.entity.ProgressCalculationMode;
import com.buildup.site.entity.Site;
import com.buildup.site.entity.SiteProcess;
import com.buildup.site.repository.SiteProcessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class SiteProgressService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final SiteProcessRepository siteProcessRepository;
    private final ReportRepository reportRepository;

    public SiteProgressService(
            SiteProcessRepository siteProcessRepository,
            ReportRepository reportRepository
    ) {
        this.siteProcessRepository = siteProcessRepository;
        this.reportRepository = reportRepository;
    }

    public BigDecimal currentProgress(Site site) {
        if (site.getProgressCalculationMode() == ProgressCalculationMode.MANUAL_INPUT) {
            return reportRepository
                    .findFirstBySiteIdAndProcessKeyIsNullAndReviewStatusOrderByCreatedAtDescIdDesc(
                            site.getId(),
                            ReportReviewStatus.APPROVED
                    )
                    .map(Report::getToProgress)
                    .orElse(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return siteProcessRepository.findAllBySiteIdOrderBySortOrderAscIdAsc(site.getId())
                .stream()
                .map(this::weightedProgress)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public void validateCompleteWeights(Site site) {
        List<SiteProcess> processes = siteProcessRepository
                .findAllBySiteIdOrderBySortOrderAscIdAsc(site.getId());
        BigDecimal total = processes.stream()
                .map(SiteProcess::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (processes.isEmpty() || total.compareTo(HUNDRED) != 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SITE_PROCESS_WEIGHT_TOTAL_INVALID",
                    "공정별 진행률 방식을 사용하려면 공정 가중치 합계가 100이어야 합니다."
            );
        }
    }

    private BigDecimal weightedProgress(SiteProcess process) {
        return process.getWeight()
                .multiply(process.getProgress())
                .divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }
}
