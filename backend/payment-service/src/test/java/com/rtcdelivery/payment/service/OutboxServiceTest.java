package com.rtcdelivery.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.payment.domain.OutboxEvent;
import com.rtcdelivery.payment.domain.OutboxStatus;
import com.rtcdelivery.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutboxService outboxService;

    @Test
    @DisplayName("이벤트 저장 시 OutboxEvent가 올바르게 영속화된다")
    void recordEvent_success() {
        outboxService.recordEvent(
                "Payment",
                "10",
                "PAYMENT_COMPLETED",
                "payment-events",
                "100",
                Map.of("paymentId", 10L, "amount", 25000)
        );

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Payment");
        assertThat(saved.getAggregateId()).isEqualTo("10");
        assertThat(saved.getEventType()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(saved.getTopic()).isEqualTo("payment-events");
        assertThat(saved.getMessageKey()).isEqualTo("100");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getPayload()).contains("25000");
    }
}
