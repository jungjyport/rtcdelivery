package com.rtcdelivery.foodcatalog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.foodcatalog.domain.OutboxEvent;
import com.rtcdelivery.foodcatalog.domain.OutboxStatus;
import com.rtcdelivery.foodcatalog.dto.event.EventEnvelope;
import com.rtcdelivery.foodcatalog.dto.event.TranslationRequestPayload;
import com.rtcdelivery.foodcatalog.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    @DisplayName("recordTranslationRequest_엔트리가_없으면_저장하지_않는다")
    void recordTranslationRequest_emptyEntries_doesNothing() {
        outboxService.recordTranslationRequest(1L, "CREATED", List.of());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordTranslationRequest_아웃박스_이벤트를_정상_생성_저장한다")
    void recordTranslationRequest_savesOutboxEvent() throws Exception {
        TranslationRequestPayload.RequestEntry entry = new TranslationRequestPayload.RequestEntry(
                "RESTAURANT", 1L, "맛있는 식당", "설명"
        );

        outboxService.recordTranslationRequest(1L, "CREATED", List.of(entry));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent event = captor.getValue();
        assertThat(event.getTopic()).isEqualTo("translation-requests");
        assertThat(event.getAggregateType()).isEqualTo("RESTAURANT");
        assertThat(event.getAggregateId()).isEqualTo("1");
        assertThat(event.getMessageKey()).isEqualTo("restaurant:1");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(0);

        EventEnvelope<TranslationRequestPayload> envelope = objectMapper.readValue(
                event.getPayload(),
                new TypeReference<>() {}
        );
        assertThat(envelope.eventType()).isEqualTo("TranslationRequested");
        assertThat(envelope.aggregateId()).isEqualTo("1");
        assertThat(envelope.payload().sourceLocale()).isEqualTo("ko");
        assertThat(envelope.payload().targetLocales()).containsExactly("ja", "en");
        assertThat(envelope.payload().reason()).isEqualTo("CREATED");
        assertThat(envelope.payload().entries()).hasSize(1);
        assertThat(envelope.payload().entries().get(0).name()).isEqualTo("맛있는 식당");
    }
}
