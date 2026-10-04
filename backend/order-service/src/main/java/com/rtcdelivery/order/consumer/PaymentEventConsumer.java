package com.rtcdelivery.order.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.order.dto.event.EventEnvelope;
import com.rtcdelivery.order.dto.event.PaymentResultPayload;
import com.rtcdelivery.order.service.InboxService;
import com.rtcdelivery.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    public static final String TOPIC_PAYMENT_EVENTS = "payment-events";
    public static final String CONSUMER_GROUP = "order-service-group";

    private final InboxService inboxService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_PAYMENT_EVENTS, groupId = CONSUMER_GROUP)
    @Transactional
    public void consume(String message) {
        try {
            EventEnvelope<PaymentResultPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<PaymentResultPayload>>() {}
            );

            if (!inboxService.markProcessed(envelope.eventId(), envelope.eventType())) {
                log.info("Duplicate payment event ignored in order-service: eventId={}", envelope.eventId());
                return;
            }

            orderService.processPaymentEvent(envelope);
        } catch (Exception e) {
            log.error("Failed to process payment event message: {}", message, e);
            throw new RuntimeException("Failed to process payment event", e);
        }
    }
}
