package com.rtcdelivery.payment.service;

import com.rtcdelivery.payment.domain.InboxEvent;
import com.rtcdelivery.payment.repository.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxService {

    private final InboxEventRepository inboxEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markProcessed(String eventId, String eventType) {
        if (inboxEventRepository.existsByEventId(eventId)) {
            return false;
        }

        try {
            inboxEventRepository.saveAndFlush(InboxEvent.of(eventId, eventType));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Inbox event already inserted concurrently: eventId={}", eventId);
            return false;
        }
    }
}
