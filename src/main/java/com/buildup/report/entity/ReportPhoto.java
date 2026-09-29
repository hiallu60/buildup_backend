package com.buildup.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportPhoto {

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    private ReportPhoto(String fileName) {
        this.fileName = fileName;
    }

    public static ReportPhoto of(String fileName) {
        return new ReportPhoto(fileName);
    }
}
