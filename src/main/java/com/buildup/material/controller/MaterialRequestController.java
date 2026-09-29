package com.buildup.material.controller;

import com.buildup.material.dto.ChangeMaterialRequestStatusRequest;
import com.buildup.material.dto.CreateMaterialRequest;
import com.buildup.material.dto.MaterialRequestDetailResponse;
import com.buildup.material.dto.MaterialRequestPageResponse;
import com.buildup.material.entity.MaterialRequestStatus;
import com.buildup.material.service.MaterialRequestService;
import com.buildup.user.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class MaterialRequestController {

    private final CurrentUserService currentUserService;
    private final MaterialRequestService materialRequestService;

    public MaterialRequestController(
            CurrentUserService currentUserService,
            MaterialRequestService materialRequestService
    ) {
        this.currentUserService = currentUserService;
        this.materialRequestService = materialRequestService;
    }

    @PostMapping("/api/sites/{siteId}/material-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialRequestDetailResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId,
            @Valid @RequestBody CreateMaterialRequest request
    ) {
        return materialRequestService.create(currentUserService.require(jwt), siteId, request);
    }

    @GetMapping("/api/sites/{siteId}/material-requests")
    public MaterialRequestPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long siteId,
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) MaterialRequestStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return materialRequestService.list(
                currentUserService.require(jwt),
                siteId,
                processKey,
                status,
                page,
                size
        );
    }

    @GetMapping("/api/material-requests/{requestId}")
    public MaterialRequestDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long requestId
    ) {
        return materialRequestService.detail(currentUserService.require(jwt), requestId);
    }

    @PatchMapping("/api/material-requests/{requestId}/status")
    public MaterialRequestDetailResponse changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long requestId,
            @Valid @RequestBody ChangeMaterialRequestStatusRequest request
    ) {
        return materialRequestService.changeStatus(
                currentUserService.require(jwt),
                requestId,
                request
        );
    }
}
