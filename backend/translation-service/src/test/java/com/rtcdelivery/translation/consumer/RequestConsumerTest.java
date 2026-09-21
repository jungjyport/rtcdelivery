package com.rtcdelivery.translation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.translation.domain.JobStatus;
import com.rtcdelivery.translation.domain.TranslationJob;
import com.rtcdelivery.translation.dto.event.EventEnvelope;
import com.rtcdelivery.translation.dto.event.TranslationRequestPayload;
import com.rtcdelivery.translation.dto.event.TranslationRequestPayload.RequestEntry;
import com.rtcdelivery.translation.repository.TranslationJobRepository;
import com.rtcdelivery.translation.service.InboxService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RequestConsumerTest {

    @Mock
    private InboxService inboxService;

    @Mock
    private TranslationJobRepository translationJobRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RequestConsumer requestConsumer;

    @Test
    @DisplayName("이미 처리된 eventId는 인박스 중복 판정으로 잡 적재 없이 스킵된다")
    void consume_duplicateEvent_skipped() throws Exception {
        TranslationRequestPayload payload = new TranslationRequestPayload(
                "ko", List.of("ja"), "CREATED",
                List.of(new RequestEntry("RESTAURANT", 1L, "식당", null))
        );
        EventEnvelope<TranslationRequestPayload> envelope = EventEnvelope.of(
                "dup-event-id", "TRANSLATION_REQUESTED", "RESTAURANT", "1", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed("dup-event-id", RequestConsumer.CONSUMER_GROUP, "TRANSLATION_REQUESTED"))
                .willReturn(false);

        requestConsumer.consume(json);

        verify(translationJobRepository, never()).save(any());
        verify(translationJobRepository, never()).supersedePendingJobs(any(), any());
    }

    @Test
    @DisplayName("신규 이벤트 수신 시 기존 대기 잡을 SUPERSEDED 처리하고 targetLocale별 잡을 적재한다")
    void consume_validEvent_supersedesOldJobsAndEnqueuesNew() throws Exception {
        TranslationRequestPayload payload = new TranslationRequestPayload(
                "ko", List.of("ja"), "UPDATED",
                List.of(new RequestEntry("RESTAURANT", 1L, "식당 신규명", "새 설명"))
        );
        EventEnvelope<TranslationRequestPayload> envelope = EventEnvelope.of(
                "new-event-id", "TRANSLATION_REQUESTED", "RESTAURANT", "1", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed("new-event-id", RequestConsumer.CONSUMER_GROUP, "TRANSLATION_REQUESTED"))
                .willReturn(true);
        given(translationJobRepository.supersedePendingJobs(1L, "ja")).willReturn(1);

        requestConsumer.consume(json);

        verify(translationJobRepository).supersedePendingJobs(1L, "ja");

        ArgumentCaptor<TranslationJob> captor = ArgumentCaptor.forClass(TranslationJob.class);
        verify(translationJobRepository).save(captor.capture());

        TranslationJob savedJob = captor.getValue();
        assertThat(savedJob.getEventId()).isEqualTo("new-event-id");
        assertThat(savedJob.getRestaurantId()).isEqualTo(1L);
        assertThat(savedJob.getTargetLocale()).isEqualTo("ja");
        assertThat(savedJob.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(savedJob.getRetryCount()).isEqualTo(0);
    }
}
