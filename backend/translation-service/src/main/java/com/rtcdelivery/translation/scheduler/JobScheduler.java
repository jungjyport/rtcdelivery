package com.rtcdelivery.translation.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.translation.domain.OutboxEvent;
import com.rtcdelivery.translation.domain.OutboxStatus;
import com.rtcdelivery.translation.domain.TranslationJob;
import com.rtcdelivery.translation.dto.event.EventEnvelope;
import com.rtcdelivery.translation.dto.event.TranslationRequestPayload.RequestEntry;
import com.rtcdelivery.translation.dto.event.TranslationResultPayload;
import com.rtcdelivery.translation.dto.event.TranslationResultPayload.ResultEntry;
import com.rtcdelivery.translation.exception.BusinessException;
import com.rtcdelivery.translation.exception.ErrorCode;
import com.rtcdelivery.translation.repository.OutboxEventRepository;
import com.rtcdelivery.translation.repository.TranslationJobRepository;
import com.rtcdelivery.translation.service.QuotaGuard;
import com.rtcdelivery.translation.service.TranslationGateService;
import com.rtcdelivery.translation.service.TranslationProvider.ItemToTranslate;
import com.rtcdelivery.translation.service.TranslationProvider.TranslatedItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobScheduler {

    public static final String TOPIC_RESULTS = "translation-results";
    public static final String TOPIC_REQUESTS_DLT = "translation-requests.DLT";

    private final TranslationJobRepository jobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TranslationGateService translationGateService;
    private final QuotaGuard quotaGuard;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 30000)
    public void schedulePendingJobs() {
        int quota = quotaGuard.getRemainingDailyQuota();
        if (quota <= 0) {
            log.debug("No remaining daily Gemini quota for translation jobs. Skipping this turn.");
            return;
        }

        int limit = Math.min(quota, 10);
        List<TranslationJob> pendingJobs = fetchAndLockJobs(limit);
        if (pendingJobs.isEmpty()) {
            return;
        }

        log.info("Processing {} translation jobs (remaining quota: {})", pendingJobs.size(), quota);
        for (TranslationJob job : pendingJobs) {
            processJob(job);
        }
    }

    @Transactional
    public List<TranslationJob> fetchAndLockJobs(int limit) {
        return jobRepository.lockPending(LocalDateTime.now(), limit);
    }

    @Transactional
    public void processJob(TranslationJob job) {
        job.markInProgress();

        try {
            List<RequestEntry> entries = objectMapper.readValue(
                    job.getEntries(), new TypeReference<>() {}
            );

            List<ItemToTranslate> items = new ArrayList<>();
            for (RequestEntry entry : entries) {
                String ref = entry.targetType() + ":" + entry.targetId();
                items.add(new ItemToTranslate(ref, entry.name(), entry.description()));
            }

            List<TranslatedItem> translatedItems = translationGateService.translateBatch(
                    job.getSourceLocale(), job.getTargetLocale(), items
            );

            List<ResultEntry> resultEntries = new ArrayList<>();
            for (TranslatedItem item : translatedItems) {
                String[] parts = item.ref().split(":", 2);
                String targetType = parts[0];
                Long targetId = Long.parseLong(parts[1]);
                resultEntries.add(new ResultEntry(targetType, targetId, item.name(), item.description()));
            }

            TranslationResultPayload resultPayload = new TranslationResultPayload(
                    job.getTargetLocale(),
                    job.getSourceLocale(),
                    resultEntries
            );

            String eventId = UUID.randomUUID().toString();
            EventEnvelope<TranslationResultPayload> resultEnvelope = EventEnvelope.of(
                    eventId,
                    "TRANSLATION_COMPLETED",
                    "RESTAURANT",
                    String.valueOf(job.getRestaurantId()),
                    resultPayload
            );

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(eventId)
                    .aggregateType("RESTAURANT")
                    .aggregateId(String.valueOf(job.getRestaurantId()))
                    .topic(TOPIC_RESULTS)
                    .eventType("TRANSLATION_COMPLETED")
                    .messageKey("restaurant:" + job.getRestaurantId())
                    .payload(objectMapper.writeValueAsString(resultEnvelope))
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .nextRetryAt(LocalDateTime.now())
                    .build();

            outboxEventRepository.save(outboxEvent);
            job.markCompleted();
            jobRepository.save(job);

            log.info("Translation job completed: jobId={}, restaurantId={}, targetLocale={}",
                    job.getId(), job.getRestaurantId(), job.getTargetLocale());

        } catch (BusinessException e) {
            handleJobException(job, e);
        } catch (Exception e) {
            handleJobException(job, e);
        }
    }

    private void handleJobException(TranslationJob job, Exception e) {
        if (e instanceof BusinessException be && be.getErrorCode() == ErrorCode.TRANSLATION_QUOTA_EXCEEDED) {
            // 쿼터 소진 시 retry_count 증가 없이 다음날로 연기 (시연용 1시간 후 재시도)
            LocalDateTime nextRetry = LocalDateTime.now().plusHours(1);
            job.markQuotaDelayed(nextRetry);
            jobRepository.save(job);
            log.warn("Job postponed due to quota: jobId={}, nextRetry={}", job.getId(), nextRetry);
            return;
        }

        log.error("Error processing job: jobId={}, retryCount={}, error={}",
                job.getId(), job.getRetryCount(), e.getMessage(), e);

        if (job.getRetryCount() >= 2) {
            // 3회 소진 시 FAILED 격리 및 DLT 발행
            job.markFailed(e.getMessage());
            jobRepository.save(job);

            try {
                kafkaTemplate.send(TOPIC_REQUESTS_DLT, "restaurant:" + job.getRestaurantId(), job.getEntries());
                log.warn("Job isolated to DLT: jobId={}, eventId={}", job.getId(), job.getEventId());
            } catch (Exception kafkaEx) {
                log.error("Failed to send job to DLT: jobId={}", job.getId(), kafkaEx);
            }
        } else {
            // 지수 백오프: 2^(retry+1) 초
            long delaySec = (long) Math.pow(2, job.getRetryCount() + 1);
            LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delaySec);
            job.markRetry(nextRetry, e.getMessage());
            jobRepository.save(job);
        }
    }
}
