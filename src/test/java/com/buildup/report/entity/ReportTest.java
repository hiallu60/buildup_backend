package com.buildup.report.entity;

import com.buildup.site.entity.Site;
import com.buildup.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTest {

    @Test
    void rejectsPhotoCheckFromAnotherReport() {
        User author = User.register("tester", "report-entity@buildup.test", null, "hash");
        Site site = Site.create(
                "site",
                "address",
                "interior",
                author,
                "REPORT-ENTITY",
                null,
                null,
                null
        );
        ReportPhoto firstPhoto = ReportPhoto.legacy("first.jpg");
        ReportPhoto secondPhoto = ReportPhoto.legacy("second.jpg");
        Report first = report(site, author, firstPhoto, "first work");
        Report second = report(site, author, secondPhoto, "second work");

        assertThatThrownBy(() -> first.addCheck(ReportCheck.forPhoto(
                secondPhoto,
                ReportCheckType.NO_LOCATION,
                "invalid"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same report");
    }

    @Test
    void deletionKeepsOrderGapsAndNewPhotoUsesNextPosition() {
        User author = User.register("tester", "report-order@buildup.test", null, "hash");
        Site site = Site.create(
                "site",
                "address",
                "interior",
                author,
                "REPORT-ORDER",
                null,
                null,
                null
        );
        ReportPhoto first = ReportPhoto.legacy("first.jpg");
        ReportPhoto second = ReportPhoto.legacy("second.jpg");
        Report report = Report.submit(
                site,
                null,
                null,
                "work",
                BigDecimal.ZERO,
                BigDecimal.TEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(first, second),
                List.of(),
                author
        );

        report.removePhoto(first);
        ReportPhoto third = ReportPhoto.legacy("third.jpg");
        report.addPhoto(third);

        assertThat(report.getPhotos())
                .extracting(ReportPhoto::getPosition)
                .containsExactly(1, 2);
    }

    private Report report(Site site, User author, ReportPhoto photo, String work) {
        return Report.submit(
                site,
                null,
                null,
                work,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(photo),
                List.of(),
                author
        );
    }
}
