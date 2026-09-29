package com.buildup.material.service;

import com.buildup.common.exception.ApiException;
import com.buildup.material.dto.ChangeMaterialRequestStatusRequest;
import com.buildup.material.dto.CreateMaterialRequest;
import com.buildup.material.dto.MaterialRequestDetailResponse;
import com.buildup.material.dto.MaterialRequestItemRequest;
import com.buildup.material.dto.MaterialRequestPageResponse;
import com.buildup.material.dto.MaterialRequestSummaryResponse;
import com.buildup.material.entity.MaterialRequest;
import com.buildup.material.entity.MaterialRequestItem;
import com.buildup.material.entity.MaterialRequestStatus;
import com.buildup.material.repository.MaterialRequestRepository;
import com.buildup.notification.service.NotificationService;
import com.buildup.site.entity.Site;
import com.buildup.site.entity.SiteProcess;
import com.buildup.site.repository.SiteProcessRepository;
import com.buildup.site.repository.SiteRepository;
import com.buildup.site.service.SiteAccessService;
import com.buildup.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MaterialRequestService {

    private final MaterialRequestRepository materialRequestRepository;
    private final SiteRepository siteRepository;
    private final SiteProcessRepository siteProcessRepository;
    private final SiteAccessService siteAccessService;
    private final NotificationService notificationService;

    public MaterialRequestService(
            MaterialRequestRepository materialRequestRepository,
            SiteRepository siteRepository,
            SiteProcessRepository siteProcessRepository,
            SiteAccessService siteAccessService,
            NotificationService notificationService
    ) {
        this.materialRequestRepository = materialRequestRepository;
        this.siteRepository = siteRepository;
        this.siteProcessRepository = siteProcessRepository;
        this.siteAccessService = siteAccessService;
        this.notificationService = notificationService;
    }

    @Transactional
    public MaterialRequestDetailResponse create(
            User author,
            Long siteId,
            CreateMaterialRequest request
    ) {
        Site site = requireSite(siteId);
        if (!siteAccessService.canSubmitMaterialRequests(author, site)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "MATERIAL_REQUEST_SUBMIT_FORBIDDEN",
                    "자재 요청을 작성할 권한이 없습니다."
            );
        }
        if (request.neededBy() != null && request.neededBy().isBefore(LocalDate.now())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MATERIAL_NEEDED_BY_IN_PAST",
                    "필요 일자는 오늘보다 이전일 수 없습니다."
            );
        }

        String processKey = request.processKey().trim();
        SiteProcess process = siteProcessRepository.findForRead(siteId, processKey)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SITE_PROCESS_NOT_FOUND",
                        "현장 공정을 찾을 수 없습니다."
                ));
        List<MaterialRequestItem> items = request.items().stream()
                .map(this::toItem)
                .toList();

        MaterialRequest materialRequest = materialRequestRepository.saveAndFlush(
                MaterialRequest.submit(
                        site,
                        process.getProcessKey(),
                        process.getName(),
                        request.neededBy(),
                        request.urgent(),
                        normalize(request.note()),
                        author,
                        items,
                        siteAccessService.isHqManager(author, site),
                        LocalDateTime.now()
                )
        );
        return MaterialRequestDetailResponse.from(materialRequest);
    }

    @Transactional(readOnly = true)
    public MaterialRequestPageResponse list(
            User user,
            Long siteId,
            String processKey,
            MaterialRequestStatus status,
            int page,
            int size
    ) {
        Site site = requireSite(siteId);
        requireSiteAccess(user, site);
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return MaterialRequestPageResponse.from(
                materialRequestRepository.search(
                        siteId,
                        normalize(processKey),
                        status,
                        pageable
                ).map(MaterialRequestSummaryResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public MaterialRequestDetailResponse detail(User user, Long requestId) {
        MaterialRequest request = materialRequestRepository.findDetailById(requestId)
                .orElseThrow(() -> requestNotFound());
        requireSiteAccess(user, request.getSite());
        return MaterialRequestDetailResponse.from(request);
    }

    @Transactional
    public MaterialRequestDetailResponse changeStatus(
            User actor,
            Long requestId,
            ChangeMaterialRequestStatusRequest command
    ) {
        MaterialRequest request = materialRequestRepository.findForUpdate(requestId)
                .orElseThrow(() -> requestNotFound());
        if (!siteAccessService.canManageMaterialRequests(actor, request.getSite())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "MATERIAL_REQUEST_MANAGEMENT_FORBIDDEN",
                    "자재 요청을 처리할 권한이 없습니다."
            );
        }

        validateTransition(request, command);
        String rejectReason = normalize(command.rejectReason());
        boolean byHq = siteAccessService.isHqManager(actor, request.getSite());
        request.transition(
                command.status(),
                rejectReason,
                actor,
                byHq,
                LocalDateTime.now()
        );
        notificationService.create(
                request.getAuthor(),
                request.getSite(),
                "MATERIAL_REQUEST_" + command.status().name(),
                statusTitle(command.status()),
                request.getSite().getName() + " · " + request.getProcessName(),
                "MATERIAL_REQUEST",
                request.getId()
        );
        return MaterialRequestDetailResponse.from(request);
    }

    private void validateTransition(
            MaterialRequest request,
            ChangeMaterialRequestStatusRequest command
    ) {
        if (!request.getStatus().canTransitionTo(command.status())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_MATERIAL_STATUS_TRANSITION",
                    request.getStatus() + " 상태에서 " + command.status() + " 상태로 변경할 수 없습니다."
            );
        }

        String rejectReason = normalize(command.rejectReason());
        if (command.status() == MaterialRequestStatus.REJECTED && rejectReason == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MATERIAL_REJECT_REASON_REQUIRED",
                    "자재 요청을 반려할 때는 사유가 필요합니다."
            );
        }
        if (command.status() != MaterialRequestStatus.REJECTED && rejectReason != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MATERIAL_REJECT_REASON_NOT_ALLOWED",
                    "반려 상태가 아니면 반려 사유를 입력할 수 없습니다."
            );
        }
    }

    private MaterialRequestItem toItem(MaterialRequestItemRequest item) {
        return MaterialRequestItem.of(
                item.name().trim(),
                item.quantity(),
                item.unit().trim()
        );
    }

    private Site requireSite(Long siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SITE_NOT_FOUND",
                        "현장을 찾을 수 없습니다."
                ));
    }

    private void requireSiteAccess(User user, Site site) {
        if (!siteAccessService.canAccessSite(user, site)) {
            throw requestNotFound();
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String statusTitle(MaterialRequestStatus status) {
        return switch (status) {
            case REQUESTED -> "자재 요청이 등록되었습니다";
            case APPROVED -> "자재 요청이 승인되었습니다";
            case REJECTED -> "자재 요청이 반려되었습니다";
            case ORDERED -> "자재 주문이 완료되었습니다";
            case DELIVERED -> "자재 배송이 완료되었습니다";
        };
    }

    private ApiException requestNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "MATERIAL_REQUEST_NOT_FOUND",
                "자재 요청을 찾을 수 없습니다."
        );
    }
}
