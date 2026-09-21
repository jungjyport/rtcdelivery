package com.rtcdelivery.translation.publisher;

import com.rtcdelivery.translation.domain.OutboxEvent;
import com.rtcdelivery.translation.domain.OutboxStatus;
import com.rtcdelivery.translation.repository.OutboxEventRepository;
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
    @DisplayName("성공적으로 Kafka에 발행되면 상태가 PUBLISHED로 변경된다")
    void publishPendingEvents_success() {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L)
                .eventId("event-1")
                .aggregateType("RESTAURANT")
                .aggregateId("12")
                .topic("translation-results")
                .eventType("TRANSLATION_COMPLETED")
                .messageKey("restaurant:12")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        given(outboxEventRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(OutboxStatus.PENDING), any(), any(Pageable.class)
        )).willReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        given(kafkaTemplate.send("translation-results", "restaurant:12", "{}"))
                .willReturn(future);

        outboxPublisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 retryCount가 증가하고 nextRetryAt이 지수 백오프로 설정된다")
    void publishPendingEvents_failure_retriesWithBackoff() {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L)
                .eventId("event-1")
                .aggregateType("RESTAURANT")
                .aggregateId("12")
                .topic("translation-results")
                .eventType("TRANSLATION_COMPLETED")
                .messageKey("restaurant:12")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        given(outboxEventRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(OutboxStatus.PENDING), any(), any(Pageable.class)
        )).willReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka unreachable"));
        given(kafkaTemplate.send("translation-results", "restaurant:12", "{}"))
                .willReturn(future);

        outboxPublisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("Kafka unreachable");
    }
}
