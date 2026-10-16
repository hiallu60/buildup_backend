package com.buildup.site.service;

import com.buildup.report.repository.ReportRepository;
import com.buildup.site.entity.ProgressCalculationMode;
import com.buildup.site.entity.Site;
import com.buildup.site.entity.SiteProcess;
import com.buildup.site.repository.SiteProcessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteProgressServiceTest {

    @Mock
    private SiteProcessRepository siteProcessRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private Site site;

    @Mock
    private SiteProcess firstProcess;

    @Mock
    private SiteProcess secondProcess;

    private SiteProgressService service;

    @BeforeEach
    void setUp() {
        service = new SiteProgressService(siteProcessRepository, reportRepository);
    }

    @Test
    void calculatesWeightedOverallProgress() {
        when(site.getId()).thenReturn(1L);
        when(site.getProgressCalculationMode()).thenReturn(ProgressCalculationMode.PROCESS_WEIGHTED);
        when(siteProcessRepository.findAllBySiteIdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(firstProcess, secondProcess));
        when(firstProcess.getWeight()).thenReturn(new BigDecimal("40.00"));
        when(firstProcess.getProgress()).thenReturn(new BigDecimal("50.00"));
        when(secondProcess.getWeight()).thenReturn(new BigDecimal("60.00"));
        when(secondProcess.getProgress()).thenReturn(new BigDecimal("25.00"));

        assertThat(service.currentProgress(site)).isEqualByComparingTo("35.00");
    }
}
