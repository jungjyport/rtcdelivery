package com.rtcdelivery.order.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.order.dto.event.EventEnvelope;
import com.rtcdelivery.order.dto.event.PaymentResultPayload;
import com.rtcdelivery.order.service.InboxService;
import com.rtcdelivery.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private InboxService inboxService;

    @Mock
    private OrderService orderService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private PaymentEventConsumer paymentEventConsumer;

    @BeforeEach
    void setUp() {
        paymentEventConsumer = new PaymentEventConsumer(inboxService, orderService, objectMapper);
    }

    @Test
    @DisplayName("이미 처리된 이벤트(Inbox 중복)이면 orderService를 호출하지 않는다")
    void consume_alreadyProcessed() throws Exception {
        String eventId = "evt-123";
        PaymentResultPayload payload = new PaymentResultPayload(100L, 10L, 5L, 25000, null);
        EventEnvelope<PaymentResultPayload> envelope = EventEnvelope.of(
                eventId, "PAYMENT_COMPLETED", "Payment", "100", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed(eventId, "PAYMENT_COMPLETED")).willReturn(false);

        paymentEventConsumer.consume(json);

        verify(orderService, never()).processPaymentEvent(any());
    }

    @Test
    @DisplayName("신규 결제 이벤트 정상 수신 시 orderService.processPaymentEvent 호출")
    void consume_success() throws Exception {
        String eventId = "evt-200";
        PaymentResultPayload payload = new PaymentResultPayload(100L, 10L, 5L, 25000, null);
        EventEnvelope<PaymentResultPayload> envelope = EventEnvelope.of(
                eventId, "PAYMENT_COMPLETED", "Payment", "100", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed(eventId, "PAYMENT_COMPLETED")).willReturn(true);

        paymentEventConsumer.consume(json);

        verify(orderService).processPaymentEvent(any());
    }
}
