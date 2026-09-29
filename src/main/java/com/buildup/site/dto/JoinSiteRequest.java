package com.buildup.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinSiteRequest(
        @NotBlank
        @Size(max = 50)
        String code
) {
}
