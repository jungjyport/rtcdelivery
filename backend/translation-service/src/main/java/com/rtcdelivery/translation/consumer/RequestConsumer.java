package com.rtcdelivery.translation.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.translation.domain.JobStatus;
import com.rtcdelivery.translation.domain.TranslationJob;
import com.rtcdelivery.translation.dto.event.EventEnvelope;
import com.rtcdelivery.translation.dto.event.TranslationRequestPayload;
import com.rtcdelivery.translation.repository.TranslationJobRepository;
import com.rtcdelivery.translation.service.InboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestConsumer {

    public static final String CONSUMER_GROUP = "translation-service-group";
    public static final String TOPIC_REQUESTS = "translation-requests";

    private final InboxService inboxService;
    private final TranslationJobRepository translationJobRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = TOPIC_REQUESTS, groupId = CONSUMER_GROUP)
    public void consume(String message) {
        try {
            EventEnvelope<TranslationRequestPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<>() {}
            );

            if (!inboxService.markProcessed(envelope.eventId(), CONSUMER_GROUP, envelope.eventType())) {
                log.info("Already processed translation request event (skipped): eventId={}", envelope.eventId());
                return;
            }

            TranslationRequestPayload payload = envelope.payload();
            if (payload == null || payload.targetLocales() == null || payload.entries() == null) {
                log.warn("Invalid translation request payload: eventId={}", envelope.eventId());
                return;
            }

            Long restaurantId = Long.parseLong(envelope.aggregateId());
            String entriesJson = objectMapper.writeValueAsString(payload.entries());

            for (String targetLocale : payload.targetLocales()) {
                // 이전 대기 중이던 잡이 있으면 SUPERSEDED 처리하여 옛 원문 번역 방지
                int supersededCount = translationJobRepository.supersedePendingJobs(restaurantId, targetLocale);
                if (supersededCount > 0) {
                    log.info("Superseded {} pending jobs for restaurantId={}, targetLocale={}",
                            supersededCount, restaurantId, targetLocale);
                }

                TranslationJob job = TranslationJob.builder()
                        .eventId(envelope.eventId())
                        .restaurantId(restaurantId)
                        .targetLocale(targetLocale)
                        .sourceLocale(payload.sourceLocale() != null ? payload.sourceLocale() : "ko")
                        .entries(entriesJson)
                        .status(JobStatus.PENDING)
                        .retryCount(0)
                        .nextRetryAt(LocalDateTime.now())
                        .build();

                translationJobRepository.save(job);
                log.info("Enqueued translation job: jobId={}, restaurantId={}, targetLocale={}",
                        job.getId(), restaurantId, targetLocale);
            }

        } catch (Exception e) {
            log.error("Failed to consume translation request message: {}", e.getMessage(), e);
            throw new RuntimeException("Kafka consumption failed", e);
        }
    }
}
