package com.rtcdelivery.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.payment.dto.event.EventEnvelope;
import com.rtcdelivery.payment.dto.event.OrderCancelledPayload;
import com.rtcdelivery.payment.dto.event.OrderCreatedPayload;
import com.rtcdelivery.payment.dto.event.OrderRefundRequestedPayload;
import com.rtcdelivery.payment.service.InboxService;
import com.rtcdelivery.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private InboxService inboxService;

    @Mock
    private PaymentService paymentService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private OrderEventConsumer orderEventConsumer;

    @BeforeEach
    void setUp() {
        orderEventConsumer = new OrderEventConsumer(inboxService, paymentService, objectMapper);
    }

    @Test
    @DisplayName("이미 처리된 이벤트(Inbox 중복)이면 paymentService를 호출하지 않는다")
    void consume_alreadyProcessed() throws Exception {
        String eventId = "evt-123";
        OrderCreatedPayload payload = new OrderCreatedPayload(10L, 5L, 1L, 25000);
        EventEnvelope<OrderCreatedPayload> envelope = EventEnvelope.of(
                eventId, "ORDER_CREATED", "Order", "10", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed(eventId, "ORDER_CREATED")).willReturn(false);

        orderEventConsumer.consume(json);

        verify(paymentService, never()).handleOrderCreated(any());
    }

    @Test
    @DisplayName("ORDER_CREATED 이벤트 정상 수신 시 handleOrderCreated 호출")
    void consume_orderCreated() throws Exception {
        String eventId = "evt-200";
        OrderCreatedPayload payload = new OrderCreatedPayload(10L, 5L, 1L, 25000);
        EventEnvelope<OrderCreatedPayload> envelope = EventEnvelope.of(
                eventId, "ORDER_CREATED", "Order", "10", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed(eventId, "ORDER_CREATED")).willReturn(true);

        orderEventConsumer.consume(json);

        verify(paymentService).handleOrderCreated(any(OrderCreatedPayload.class));
    }

    @Test
    @DisplayName("ORDER_CANCELLED 이벤트 정상 수신 시 handleOrderCancelled 호출")
    void consume_orderCancelled() throws Exception {
        String eventId = "evt-201";
        OrderCancelledPayload payload = new OrderCancelledPayload(10L, 5L, "USER_CANCELLED");
        EventEnvelope<OrderCancelledPayload> envelope = EventEnvelope.of(
                eventId, "ORDER_CANCELLED", "Order", "10", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed(eventId, "ORDER_CANCELLED")).willReturn(true);

        orderEventConsumer.consume(json);

        verify(paymentService).handleOrderCancelled(any(OrderCancelledPayload.class));
    }

    @Test
    @DisplayName("ORDER_REFUND_REQUESTED 이벤트 정상 수신 시 handleOrderRefundRequested 호출")
    void consume_orderRefundRequested() throws Exception {
        String eventId = "evt-202";
        OrderRefundRequestedPayload payload = new OrderRefundRequestedPayload(10L, 5L, 25000, "ADMIN_REFUND");
        EventEnvelope<OrderRefundRequestedPayload> envelope = EventEnvelope.of(
                eventId, "ORDER_REFUND_REQUESTED", "Order", "10", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed(eventId, "ORDER_REFUND_REQUESTED")).willReturn(true);

        orderEventConsumer.consume(json);

        verify(paymentService).handleOrderRefundRequested(any(OrderRefundRequestedPayload.class));
    }
}
