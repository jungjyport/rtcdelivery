package com.rtcdelivery.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.payment.domain.OutboxEvent;
import com.rtcdelivery.payment.domain.OutboxStatus;
import com.rtcdelivery.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void recordEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String messageKey,
            Object payload
    ) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .messageKey(messageKey)
                    .payload(payloadJson)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .nextRetryAt(LocalDateTime.now())
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.info("Recorded payment outbox event: aggregateType={}, aggregateId={}, eventType={}, topic={}",
                    aggregateType, aggregateId, eventType, topic);
        } catch (Exception e) {
            log.error("Failed to serialize or record payment outbox event: aggregateId={}, eventType={}",
                    aggregateId, eventType, e);
            throw new RuntimeException("Failed to record outbox event", e);
        }
    }
}
