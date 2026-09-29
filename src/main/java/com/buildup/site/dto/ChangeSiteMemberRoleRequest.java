package com.buildup.site.dto;

import com.buildup.site.entity.SiteMemberRole;
import jakarta.validation.constraints.NotNull;

public record ChangeSiteMemberRoleRequest(
        @NotNull
        SiteMemberRole role
) {
}
