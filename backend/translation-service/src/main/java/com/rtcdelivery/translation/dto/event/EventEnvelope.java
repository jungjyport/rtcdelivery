package com.rtcdelivery.translation.dto.event;

import java.time.Instant;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String occurredAt,
        T payload
) {
    public static <T> EventEnvelope<T> of(String eventId,
                                          String eventType,
                                          String aggregateType,
                                          String aggregateId,
                                          T payload) {
        return new EventEnvelope<>(eventId, eventType, aggregateType, aggregateId, Instant.now().toString(), payload);
    }
}
