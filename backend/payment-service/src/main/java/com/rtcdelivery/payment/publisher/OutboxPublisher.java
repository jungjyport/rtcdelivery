package com.rtcdelivery.payment.publisher;

import com.rtcdelivery.payment.domain.OutboxEvent;
import com.rtcdelivery.payment.domain.OutboxStatus;
import com.rtcdelivery.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository
                .findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        OutboxStatus.PENDING,
                        LocalDateTime.now(),
                        PageRequest.of(0, 50)
                );

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()).get();
                event.markPublished();
                log.info("Published payment outbox event: id={}, topic={}, messageKey={}",
                        event.getId(), event.getTopic(), event.getMessageKey());
            } catch (Exception e) {
                log.error("Failed to publish payment outbox event: id={}, retryCount={}, error={}",
                        event.getId(), event.getRetryCount(), e.getMessage());

                if (event.getRetryCount() >= 30) {
                    event.markFailed(e.getMessage());
                } else {
                    long delay = Math.min(300, (long) Math.pow(2, event.getRetryCount()));
                    event.markRetry(LocalDateTime.now().plusSeconds(delay), e.getMessage());
                }
            }
        }
    }
}
