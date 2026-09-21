package com.rtcdelivery.translation.service;

import com.rtcdelivery.translation.domain.InboxEvent;
import com.rtcdelivery.translation.repository.InboxEventRepository;
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
    @DisplayName("최초 이벤트 수신 시 markProcessed는 true를 반환한다")
    void markProcessed_firstTime_returnsTrue() {
        given(inboxEventRepository.saveAndFlush(any(InboxEvent.class)))
                .willReturn(InboxEvent.builder().build());

        boolean result = inboxService.markProcessed("event-1", "test-group", "TRANSLATION_REQUESTED");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("중복 이벤트 수신으로 유니크 제약 위반 발생 시 false를 반환한다")
    void markProcessed_duplicate_returnsFalse() {
        given(inboxEventRepository.saveAndFlush(any(InboxEvent.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate"));

        boolean result = inboxService.markProcessed("event-1", "test-group", "TRANSLATION_REQUESTED");
        assertThat(result).isFalse();
    }
}
