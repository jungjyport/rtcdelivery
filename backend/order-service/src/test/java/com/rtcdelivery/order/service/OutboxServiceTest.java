package com.rtcdelivery.order.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.order.domain.FulfillmentStatus;
import com.rtcdelivery.order.domain.Order;
import com.rtcdelivery.order.domain.OutboxEvent;
import com.rtcdelivery.order.domain.OutboxStatus;
import com.rtcdelivery.order.domain.PaymentStatus;
import com.rtcdelivery.order.dto.event.EventEnvelope;
import com.rtcdelivery.order.dto.event.OrderCancelledPayload;
import com.rtcdelivery.order.dto.event.OrderCreatedPayload;
import com.rtcdelivery.order.dto.event.OrderRefundRequestedPayload;
import com.rtcdelivery.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutboxService outboxService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.builder()
                .id(100L)
                .memberId(5L)
                .restaurantId(1L)
                .restaurantOwnerId(10L)
                .restaurantName("서울 김치찌개")
                .fulfillmentStatus(FulfillmentStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .foodAmount(18000)
                .deliveryFee(3000)
                .totalAmount(21000)
                .recipientName("홍길동")
                .recipientPhone("010-1234-5678")
                .address("서울시 강남구")
                .idempotencyKey("uuid-1234")
                .build();
    }

    @Test
    @DisplayName("recordOrderCreated_ORDER_CREATED_이벤트를_Outbox에_저장한다")
    void recordOrderCreated_savesOutboxEvent() throws Exception {
        outboxService.recordOrderCreated(order);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent event = captor.getValue();
        assertThat(event.getTopic()).isEqualTo("order-events");
        assertThat(event.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(event.getAggregateType()).isEqualTo("ORDER");
        assertThat(event.getAggregateId()).isEqualTo("100");
        assertThat(event.getMessageKey()).isEqualTo("order:100");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);

        EventEnvelope<OrderCreatedPayload> envelope = objectMapper.readValue(
                event.getPayload(),
                new TypeReference<>() {}
        );
        assertThat(envelope.eventType()).isEqualTo("ORDER_CREATED");
        assertThat(envelope.payload().orderId()).isEqualTo(100L);
        assertThat(envelope.payload().memberId()).isEqualTo(5L);
        assertThat(envelope.payload().totalAmount()).isEqualTo(21000);
    }

    @Test
    @DisplayName("recordOrderCancelled_ORDER_CANCELLED_이벤트를_Outbox에_저장한다")
    void recordOrderCancelled_savesOutboxEvent() throws Exception {
        outboxService.recordOrderCancelled(order, "CUSTOMER");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("ORDER_CANCELLED");

        EventEnvelope<OrderCancelledPayload> envelope = objectMapper.readValue(
                event.getPayload(),
                new TypeReference<>() {}
        );
        assertThat(envelope.payload().reason()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("recordOrderRefundRequested_ORDER_REFUND_REQUESTED_이벤트를_Outbox에_저장한다")
    void recordOrderRefundRequested_savesOutboxEvent() throws Exception {
        outboxService.recordOrderRefundRequested(order);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("ORDER_REFUND_REQUESTED");

        EventEnvelope<OrderRefundRequestedPayload> envelope = objectMapper.readValue(
                event.getPayload(),
                new TypeReference<>() {}
        );
        assertThat(envelope.payload().orderId()).isEqualTo(100L);
    }
}
