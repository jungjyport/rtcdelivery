package com.rtcdelivery.foodcatalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.foodcatalog.domain.OutboxEvent;
import com.rtcdelivery.foodcatalog.domain.OutboxStatus;
import com.rtcdelivery.foodcatalog.dto.event.EventEnvelope;
import com.rtcdelivery.foodcatalog.dto.event.TranslationRequestPayload;
import com.rtcdelivery.foodcatalog.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    public static final String TOPIC = "translation-requests";
    public static final String EVENT_TYPE = "TranslationRequested";
    public static final String AGGREGATE_TYPE = "RESTAURANT";
    public static final String SOURCE_LOCALE = "ko";
    public static final List<String> TARGET_LOCALES = List.of("ja", "en");

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTranslationRequest(Long restaurantId, String reason, List<TranslationRequestPayload.RequestEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        try {
            String eventId = UUID.randomUUID().toString();
            String aggregateId = restaurantId.toString();
            String messageKey = "restaurant:" + aggregateId;

            TranslationRequestPayload payload = new TranslationRequestPayload(
                    SOURCE_LOCALE,
                    TARGET_LOCALES,
                    reason,
                    entries
            );

            EventEnvelope<TranslationRequestPayload> envelope = EventEnvelope.of(
                    eventId,
                    EVENT_TYPE,
                    AGGREGATE_TYPE,
                    aggregateId,
                    payload
            );

            String jsonPayload = objectMapper.writeValueAsString(envelope);

            OutboxEvent event = OutboxEvent.builder()
                    .eventId(eventId)
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(aggregateId)
                    .topic(TOPIC)
                    .eventType(EVENT_TYPE)
                    .messageKey(messageKey)
                    .payload(jsonPayload)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .nextRetryAt(LocalDateTime.now())
                    .build();

            outboxEventRepository.save(event);
            log.info("Recorded translation request outbox event: eventId={}, restaurantId={}, reason={}",
                    eventId, restaurantId, reason);
        } catch (Exception e) {
            log.error("Failed to record outbox event for restaurant: {}", restaurantId, e);
            throw new RuntimeException("Failed to serialize translation request outbox event", e);
        }
    }
}
