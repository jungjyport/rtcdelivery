package com.rtcdelivery.translation.service;

import com.rtcdelivery.translation.domain.JobStatus;
import com.rtcdelivery.translation.domain.TranslationJob;
import com.rtcdelivery.translation.dto.response.TranslationJobAdminResponse;
import com.rtcdelivery.translation.exception.BusinessException;
import com.rtcdelivery.translation.exception.ErrorCode;
import com.rtcdelivery.translation.repository.TranslationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTranslationJobService {

    private final TranslationJobRepository translationJobRepository;

    @Transactional(readOnly = true)
    public Page<TranslationJobAdminResponse> getJobs(JobStatus status, Pageable pageable) {
        Page<TranslationJob> page;
        if (status != null) {
            page = translationJobRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            page = translationJobRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(TranslationJobAdminResponse::from);
    }

    @Transactional
    public TranslationJobAdminResponse retryJob(Long jobId) {
        TranslationJob job = translationJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSLATION_JOB_NOT_FOUND, "Job not found: " + jobId));

        if (job.getStatus() == JobStatus.IN_PROGRESS || job.getStatus() == JobStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.TRANSLATION_JOB_NOT_RETRYABLE,
                    "Job status " + job.getStatus() + " cannot be retried.");
        }

        job.resetForRetry();
        TranslationJob saved = translationJobRepository.save(job);
        log.info("Admin reset translation job for retry: jobId={}, status={}", saved.getId(), saved.getStatus());
        return TranslationJobAdminResponse.from(saved);
    }

    @Transactional
    public Map<String, Object> retryAllFailedJobs() {
        int updatedCount = translationJobRepository.retryAllFailedJobs(LocalDateTime.now());
        log.info("Admin reset all failed translation jobs: count={}", updatedCount);
        return Map.of("retriedCount", updatedCount);
    }
}
