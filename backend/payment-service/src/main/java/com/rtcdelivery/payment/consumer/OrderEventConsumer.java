package com.rtcdelivery.payment.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.payment.dto.event.EventEnvelope;
import com.rtcdelivery.payment.dto.event.OrderCancelledPayload;
import com.rtcdelivery.payment.dto.event.OrderCreatedPayload;
import com.rtcdelivery.payment.dto.event.OrderRefundRequestedPayload;
import com.rtcdelivery.payment.service.InboxService;
import com.rtcdelivery.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    public static final String TOPIC_ORDER_EVENTS = "order-events";
    public static final String CONSUMER_GROUP = "payment-service-group";

    private final InboxService inboxService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_ORDER_EVENTS, groupId = CONSUMER_GROUP)
    @Transactional
    public void consume(String message) {
        try {
            JsonNode rootNode = objectMapper.readTree(message);
            String eventId = rootNode.path("eventId").asText();
            String eventType = rootNode.path("eventType").asText();

            if (!inboxService.markProcessed(eventId, eventType)) {
                log.info("Duplicate order event ignored in payment-service: eventId={}", eventId);
                return;
            }

            switch (eventType) {
                case "ORDER_CREATED" -> {
                    EventEnvelope<OrderCreatedPayload> envelope = objectMapper.readValue(
                            message, new TypeReference<EventEnvelope<OrderCreatedPayload>>() {}
                    );
                    paymentService.handleOrderCreated(envelope.payload());
                }
                case "ORDER_CANCELLED" -> {
                    EventEnvelope<OrderCancelledPayload> envelope = objectMapper.readValue(
                            message, new TypeReference<EventEnvelope<OrderCancelledPayload>>() {}
                    );
                    paymentService.handleOrderCancelled(envelope.payload());
                }
                case "ORDER_REFUND_REQUESTED" -> {
                    EventEnvelope<OrderRefundRequestedPayload> envelope = objectMapper.readValue(
                            message, new TypeReference<EventEnvelope<OrderRefundRequestedPayload>>() {}
                    );
                    paymentService.handleOrderRefundRequested(envelope.payload());
                }
                default -> log.warn("Unhandled order event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process order event message: {}", message, e);
            throw new RuntimeException("Failed to process order event", e);
        }
    }
}
