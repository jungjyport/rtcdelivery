package com.rtcdelivery.foodcatalog.publisher;

import com.rtcdelivery.foodcatalog.domain.OutboxEvent;
import com.rtcdelivery.foodcatalog.domain.OutboxStatus;
import com.rtcdelivery.foodcatalog.repository.OutboxEventRepository;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("publishPendingEvents_성공시_PUBLISHED로_변경")
    void publishPendingEvents_success() {
        OutboxEvent event = OutboxEvent.builder()
                .eventId("event-1")
                .aggregateType("RESTAURANT")
                .aggregateId("1")
                .topic("translation-requests")
                .eventType("TranslationRequested")
                .messageKey("restaurant:1")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        given(outboxEventRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)
        )).willReturn(List.of(event));

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        given(kafkaTemplate.send(eq("translation-requests"), eq("restaurant:1"), eq("{}"))).willReturn(future);

        outboxPublisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("publishPendingEvents_실패시_재시도_스케줄링")
    void publishPendingEvents_failure_retries() {
        OutboxEvent event = OutboxEvent.builder()
                .eventId("event-1")
                .aggregateType("RESTAURANT")
                .aggregateId("1")
                .topic("translation-requests")
                .eventType("TranslationRequested")
                .messageKey("restaurant:1")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        given(outboxEventRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)
        )).willReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unreachable"));
        given(kafkaTemplate.send(any(), any(), any())).willReturn(failed);

        outboxPublisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("Kafka unreachable");
    }

    @Test
    @DisplayName("publishPendingEvents_30회_초과실패시_FAILED")
    void publishPendingEvents_maxRetry_marksFailed() {
        OutboxEvent event = OutboxEvent.builder()
                .eventId("event-1")
                .aggregateType("RESTAURANT")
                .aggregateId("1")
                .topic("translation-requests")
                .eventType("TranslationRequested")
                .messageKey("restaurant:1")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .retryCount(30)
                .nextRetryAt(LocalDateTime.now())
                .build();

        given(outboxEventRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)
        )).willReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unreachable"));
        given(kafkaTemplate.send(any(), any(), any())).willReturn(failed);

        outboxPublisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
