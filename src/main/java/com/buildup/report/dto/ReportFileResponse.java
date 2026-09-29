package com.buildup.report.dto;

import com.buildup.report.entity.ReportFile;

public record ReportFileResponse(
        String path,
        String originalName,
        Long sizeBytes
) {
    public static ReportFileResponse from(ReportFile file) {
        return new ReportFileResponse(file.getPath(), file.getOriginalName(), file.getSizeBytes());
    }
}
