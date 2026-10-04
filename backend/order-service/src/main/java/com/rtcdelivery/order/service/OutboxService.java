package com.rtcdelivery.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.order.domain.Order;
import com.rtcdelivery.order.domain.OutboxEvent;
import com.rtcdelivery.order.domain.OutboxStatus;
import com.rtcdelivery.order.dto.event.EventEnvelope;
import com.rtcdelivery.order.dto.event.OrderCancelledPayload;
import com.rtcdelivery.order.dto.event.OrderCreatedPayload;
import com.rtcdelivery.order.dto.event.OrderRefundRequestedPayload;
import com.rtcdelivery.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    public static final String TOPIC_ORDER_EVENTS = "order-events";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordOrderCreated(Order order) {
        OrderCreatedPayload payload = new OrderCreatedPayload(
                order.getId(),
                order.getMemberId(),
                order.getRestaurantId(),
                order.getTotalAmount(),
                "KRW"
        );
        saveEvent("ORDER_CREATED", String.valueOf(order.getId()), payload);
    }

    @Transactional
    public void recordOrderCancelled(Order order, String reason) {
        OrderCancelledPayload payload = new OrderCancelledPayload(
                order.getId(),
                order.getMemberId(),
                reason
        );
        saveEvent("ORDER_CANCELLED", String.valueOf(order.getId()), payload);
    }

    @Transactional
    public void recordOrderRefundRequested(Order order) {
        OrderRefundRequestedPayload payload = new OrderRefundRequestedPayload(
                order.getId(),
                order.getMemberId()
        );
        saveEvent("ORDER_REFUND_REQUESTED", String.valueOf(order.getId()), payload);
    }

    private <T> void saveEvent(String eventType, String orderId, T payload) {
        String eventId = UUID.randomUUID().toString();
        EventEnvelope<T> envelope = EventEnvelope.of(
                eventId,
                eventType,
                "ORDER",
                orderId,
                payload
        );

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("ORDER")
                .aggregateId(orderId)
                .topic(TOPIC_ORDER_EVENTS)
                .eventType(eventType)
                .messageKey("order:" + orderId)
                .payload(json)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        outboxEventRepository.save(outboxEvent);
        log.info("Recorded order outbox event: eventId={}, eventType={}, orderId={}", eventId, eventType, orderId);
    }
}
