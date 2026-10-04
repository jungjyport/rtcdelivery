package com.rtcdelivery.payment.publisher;

import com.rtcdelivery.payment.domain.OutboxEvent;
import com.rtcdelivery.payment.domain.OutboxStatus;
import com.rtcdelivery.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("미발행 이벤트가 없으면 Kafka 발행을 호출하지 않는다")
    void publishPendingEvents_empty() {
        given(outboxEventRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)
        )).willReturn(Collections.emptyList());

        outboxPublisher.publishPendingEvents();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("미발행 이벤트를 Kafka로 발행 성공 시 markPublished를 호출한다")
    void publishPendingEvents_success() {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L)
                .aggregateType("Payment")
                .aggregateId("10")
                .eventType("PAYMENT_COMPLETED")
                .topic("payment-events")
                .messageKey("10")
                .payload("{\"paymentId\":10}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();

        given(outboxEventRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)
        )).willReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        given(kafkaTemplate.send(eq("payment-events"), eq("10"), eq("{\"paymentId\":10}"))).willReturn(future);

        outboxPublisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 retryCount < 30이면 markRetry를 수행한다")
    void publishPendingEvents_retry() {
        OutboxEvent event = OutboxEvent.builder()
                .id(2L)
                .aggregateType("Payment")
                .aggregateId("20")
                .eventType("PAYMENT_COMPLETED")
                .topic("payment-events")
                .messageKey("20")
                .payload("{\"paymentId\":20}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();

        given(outboxEventRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)
        )).willReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        given(kafkaTemplate.send(eq("payment-events"), eq("20"), eq("{\"paymentId\":20}"))).willReturn(future);

        outboxPublisher.publishPendingEvents();

        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getLastError()).isEqualTo("java.lang.RuntimeException: Kafka error");
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 retryCount >= 30이면 markFailed를 수행한다")
    void publishPendingEvents_failed() {
        OutboxEvent event = OutboxEvent.builder()
                .id(3L)
                .aggregateType("Payment")
                .aggregateId("30")
                .eventType("PAYMENT_COMPLETED")
                .topic("payment-events")
                .messageKey("30")
                .payload("{\"paymentId\":30}")
                .status(OutboxStatus.PENDING)
                .retryCount(30)
                .build();

        given(outboxEventRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)
        )).willReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka final error"));
        given(kafkaTemplate.send(eq("payment-events"), eq("30"), eq("{\"paymentId\":30}"))).willReturn(future);

        outboxPublisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getLastError()).isEqualTo("java.lang.RuntimeException: Kafka final error");
    }
}
