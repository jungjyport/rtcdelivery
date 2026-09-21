package com.rtcdelivery.foodcatalog.service;

import com.rtcdelivery.foodcatalog.domain.InboxEvent;
import com.rtcdelivery.foodcatalog.repository.InboxEventRepository;
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

    private final InboxEventRepository inboxEventRepository;

    @Transactional
    public boolean markProcessed(String eventId, String consumerGroup, String eventType) {
        InboxEvent event = InboxEvent.builder()
                .eventId(eventId)
                .consumerGroup(consumerGroup)
                .eventType(eventType)
                .processedAt(LocalDateTime.now())
                .build();

        try {
            inboxEventRepository.saveAndFlush(event);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate event skipped in food-catalog inbox: eventId={}, consumerGroup={}",
                    eventId, consumerGroup);
            return false;
        }
    }
}
