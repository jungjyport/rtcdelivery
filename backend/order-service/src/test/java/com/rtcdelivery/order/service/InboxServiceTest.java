package com.rtcdelivery.order.service;

import com.rtcdelivery.order.domain.InboxEvent;
import com.rtcdelivery.order.repository.InboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock
    private InboxEventRepository inboxEventRepository;

    @InjectMocks
    private InboxService inboxService;

    @Test
    @DisplayName("markProcessed_처음_수신한_이벤트는_저장하고_true를_반환한다")
    void markProcessed_firstTime_returnsTrue() {
        given(inboxEventRepository.findByEventIdAndConsumerGroup("event-1", InboxService.CONSUMER_GROUP))
                .willReturn(Optional.empty());

        boolean processed = inboxService.markProcessed("event-1", "PAYMENT_COMPLETED");

        assertThat(processed).isTrue();
        verify(inboxEventRepository).save(any(InboxEvent.class));
    }

    @Test
    @DisplayName("markProcessed_이미_처리된_이벤트는_저장하지_않고_false를_반환한다")
    void markProcessed_duplicate_returnsFalse() {
        given(inboxEventRepository.findByEventIdAndConsumerGroup("event-dup", InboxService.CONSUMER_GROUP))
                .willReturn(Optional.of(InboxEvent.builder().build()));

        boolean processed = inboxService.markProcessed("event-dup", "PAYMENT_COMPLETED");

        assertThat(processed).isFalse();
    }

    @Test
    @DisplayName("markProcessed_동시_저장으로_데이터_무결성_예외_발생_시_false를_반환한다")
    void markProcessed_concurrentDuplicate_returnsFalse() {
        given(inboxEventRepository.findByEventIdAndConsumerGroup("event-concurrent", InboxService.CONSUMER_GROUP))
                .willReturn(Optional.empty());
        given(inboxEventRepository.save(any())).willThrow(new DataIntegrityViolationException("Duplicate"));

        boolean processed = inboxService.markProcessed("event-concurrent", "PAYMENT_COMPLETED");

        assertThat(processed).isFalse();
    }
}
