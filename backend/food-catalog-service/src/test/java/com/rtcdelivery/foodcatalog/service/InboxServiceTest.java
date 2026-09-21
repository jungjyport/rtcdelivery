package com.rtcdelivery.foodcatalog.service;

import com.rtcdelivery.foodcatalog.domain.InboxEvent;
import com.rtcdelivery.foodcatalog.repository.InboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock
    private InboxEventRepository inboxEventRepository;

    @InjectMocks
    private InboxService inboxService;

    @Test
    @DisplayName("markProcessed_처음_수신한_이벤트는_저장_성공하고_true_반환")
    void markProcessed_firstTime_returnsTrue() {
        boolean result = inboxService.markProcessed("event-1", "group-1", "type-1");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("markProcessed_이미_처리된_이벤트는_중복키_예외_발생하여_false_반환")
    void markProcessed_duplicate_returnsFalse() {
        given(inboxEventRepository.saveAndFlush(any(InboxEvent.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        boolean result = inboxService.markProcessed("event-dup", "group-1", "type-1");
        assertThat(result).isFalse();
    }
}
