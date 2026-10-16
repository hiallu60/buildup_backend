package com.buildup.report.service;

import com.buildup.report.entity.Report;
import com.buildup.report.entity.ReportCheckType;
import com.buildup.report.entity.ReportPhoto;
import com.buildup.site.entity.Site;
import com.buildup.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCheckServiceTest {

    private final ReportCheckService service = new ReportCheckService();

    @Test
    void addsNoLocationWhenSubmissionCoordinatesAreMissing() {
        Report report = report(site("37.5665000", "126.9780000"), null, null);

        service.addSubmissionChecks(report);

        assertThat(report.getChecks())
                .extracting(check -> check.getType())
                .containsExactly(ReportCheckType.NO_LOCATION);
    }

    @Test
    void addsFarSubmitWhenSubmissionIsOverFiveHundredMetersAway() {
        Report report = report(
                site("37.5665000", "126.9780000"),
                new BigDecimal("37.5765000"),
                new BigDecimal("126.9780000")
        );

        service.addSubmissionChecks(report);

        assertThat(report.getChecks())
                .extracting(check -> check.getType())
                .containsExactly(ReportCheckType.FAR_SUBMIT);
    }

    @Test
    void addsOldAndFarPhotoChecks() {
        Report report = report(
                site("37.5665000", "126.9780000"),
                new BigDecimal("37.5665000"),
                new BigDecimal("126.9780000")
        );
        LocalDateTime now = LocalDateTime.of(2026, 10, 15, 12, 0);

        ReportPhoto photo = ReportPhoto.uploaded(
                "reports/1/photos/photo.jpg",
                "photo.jpg",
                "image/jpeg",
                100L,
                now.minusHours(25),
                new BigDecimal("37.5765000"),
                new BigDecimal("126.9780000")
        );
        report.addPhoto(photo);

        service.addPhotoChecks(report, photo, now);

        assertThat(report.getChecks())
                .extracting(check -> check.getType())
                .containsExactly(ReportCheckType.OLD_PHOTO, ReportCheckType.FAR_PHOTO);
    }

    private Site site(String latitude, String longitude) {
        User owner = User.register("owner", "owner@buildup.test", null, "hash");
        return Site.create(
                "현장",
                "서울",
                "건축",
                owner,
                "TESTCODE01",
                null,
                new BigDecimal(latitude),
                new BigDecimal(longitude)
        );
    }

    private Report report(Site site, BigDecimal latitude, BigDecimal longitude) {
        User author = User.register("author", "author@buildup.test", null, "hash");
        return Report.submit(
                site,
                "FOUNDATION",
                "기초 공사",
                "철근 배근",
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                null,
                null,
                null,
                null,
                latitude,
                longitude,
                new BigDecimal("10.00"),
                List.of(),
                List.of(),
                author
        );
    }
}
