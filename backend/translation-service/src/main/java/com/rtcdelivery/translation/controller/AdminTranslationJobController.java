package com.rtcdelivery.translation.controller;

import com.rtcdelivery.translation.common.ApiResponse;
import com.rtcdelivery.translation.domain.JobStatus;
import com.rtcdelivery.translation.dto.response.TranslationJobAdminResponse;
import com.rtcdelivery.translation.service.AdminTranslationJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Admin Translations", description = "관리자 번역 작업 관리 API")
@RestController
@RequestMapping("/api/v1/translations/admin/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTranslationJobController {

    private final AdminTranslationJobService adminTranslationJobService;

    @Operation(summary = "번역 작업 목록 조회 (관리자)", description = "상태별 필터링 및 페이징된 번역 잡 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<Page<TranslationJobAdminResponse>> getJobs(
            @RequestParam(required = false) JobStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<TranslationJobAdminResponse> result = adminTranslationJobService.getJobs(status, pageable);
        return ApiResponse.success(result);
    }

    @Operation(summary = "단일 번역 작업 수동 재시도 (관리자)", description = "실패/대체된 특정 번역 잡을 PENDING 상태로 초기화하여 재시도합니다.")
    @PostMapping("/{jobId}/retry")
    public ApiResponse<TranslationJobAdminResponse> retryJob(@PathVariable Long jobId) {
        TranslationJobAdminResponse result = adminTranslationJobService.retryJob(jobId);
        return ApiResponse.success("Job marked for retry successfully", result);
    }

    @Operation(summary = "실패한 모든 번역 작업 일괄 재시도 (관리자)", description = "FAILED 상태의 모든 번역 잡을 PENDING 상태로 일괄 초기화합니다.")
    @PostMapping("/retry-all-failed")
    public ApiResponse<Map<String, Object>> retryAllFailed() {
        Map<String, Object> result = adminTranslationJobService.retryAllFailedJobs();
        return ApiResponse.success("All failed jobs marked for retry", result);
    }
}
