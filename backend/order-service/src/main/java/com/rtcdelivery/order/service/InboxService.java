package com.rtcdelivery.order.service;

import com.rtcdelivery.order.domain.InboxEvent;
import com.rtcdelivery.order.repository.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxService {

    public static final String CONSUMER_GROUP = "order-service-group";

    private final InboxEventRepository inboxEventRepository;

    @Transactional
    public boolean markProcessed(String eventId, String eventType) {
        if (inboxEventRepository.findByEventIdAndConsumerGroup(eventId, CONSUMER_GROUP).isPresent()) {
            log.debug("Duplicate event skipped in order-service inbox: eventId={}", eventId);
            return false;
        }

        try {
            inboxEventRepository.save(InboxEvent.builder()
                    .eventId(eventId)
                    .consumerGroup(CONSUMER_GROUP)
                    .eventType(eventType)
                    .processedAt(LocalDateTime.now())
                    .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent duplicate event skipped in order-service inbox: eventId={}", eventId);
            return false;
        }
    }
}
