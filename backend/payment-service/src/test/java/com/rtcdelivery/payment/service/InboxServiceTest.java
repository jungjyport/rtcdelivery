package com.rtcdelivery.payment.service;

import com.rtcdelivery.payment.domain.InboxEvent;
import com.rtcdelivery.payment.repository.InboxEventRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock
    private InboxEventRepository inboxEventRepository;

    @InjectMocks
    private InboxService inboxService;

    @Test
    @DisplayName("이미 존재하는 이벤트 ID면 false를 반환하고 저장을 건너뛴다")
    void markProcessed_alreadyExists() {
        String eventId = "evt-1";
        given(inboxEventRepository.existsByEventId(eventId)).willReturn(true);

        boolean result = inboxService.markProcessed(eventId, "ORDER_CREATED");

        assertThat(result).isFalse();
        verify(inboxEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("처음 처리되는 이벤트 ID면 저장하고 true를 반환한다")
    void markProcessed_new() {
        String eventId = "evt-2";
        given(inboxEventRepository.existsByEventId(eventId)).willReturn(false);

        boolean result = inboxService.markProcessed(eventId, "ORDER_CREATED");

        assertThat(result).isTrue();
        verify(inboxEventRepository).saveAndFlush(any(InboxEvent.class));
    }

    @Test
    @DisplayName("동시 삽입으로 유니크 제약 위반 발생 시 false를 반환한다")
    void markProcessed_concurrentViolation() {
        String eventId = "evt-3";
        given(inboxEventRepository.existsByEventId(eventId)).willReturn(false);
        given(inboxEventRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicate"));

        boolean result = inboxService.markProcessed(eventId, "ORDER_CREATED");

        assertThat(result).isFalse();
    }
}
