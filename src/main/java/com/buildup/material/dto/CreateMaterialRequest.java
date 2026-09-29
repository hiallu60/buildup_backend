package com.buildup.material.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateMaterialRequest(
        @NotBlank
        @Size(max = 50)
        String processKey,

        LocalDate neededBy,

        boolean urgent,

        @Size(max = 10000)
        String note,

        @NotEmpty
        @Size(max = 100)
        List<@Valid MaterialRequestItemRequest> items
) {
}
