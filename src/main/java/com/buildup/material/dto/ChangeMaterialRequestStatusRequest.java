package com.buildup.material.dto;

import com.buildup.material.entity.MaterialRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeMaterialRequestStatusRequest(
        @NotNull
        MaterialRequestStatus status,

        @Size(max = 2000)
        String rejectReason
) {
}
