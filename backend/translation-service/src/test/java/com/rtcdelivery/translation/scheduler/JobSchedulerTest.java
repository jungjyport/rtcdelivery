package com.rtcdelivery.translation.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.translation.domain.JobStatus;
import com.rtcdelivery.translation.domain.OutboxEvent;
import com.rtcdelivery.translation.domain.TranslationJob;
import com.rtcdelivery.translation.exception.BusinessException;
import com.rtcdelivery.translation.exception.ErrorCode;
import com.rtcdelivery.translation.repository.OutboxEventRepository;
import com.rtcdelivery.translation.repository.TranslationJobRepository;
import com.rtcdelivery.translation.service.QuotaGuard;
import com.rtcdelivery.translation.service.TranslationGateService;
import com.rtcdelivery.translation.service.TranslationProvider.TranslatedItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobSchedulerTest {

    @Mock
    private TranslationJobRepository jobRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TranslationGateService translationGateService;

    @Mock
    private QuotaGuard quotaGuard;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private JobScheduler jobScheduler;

    private TranslationJob sampleJob;

    @BeforeEach
    void setUp() {
        sampleJob = TranslationJob.builder()
                .id(1L)
                .eventId("event-1")
                .restaurantId(12L)
                .targetLocale("ja")
                .sourceLocale("ko")
                .entries("[{\"targetType\":\"RESTAURANT\",\"targetId\":12,\"name\":\"골목식당\",\"description\":\"노포\"}]")
                .status(JobStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("남은 쿼터가 0이면 잡 조회조차 하지 않고 스케줄러를 종료한다")
    void schedulePendingJobs_quotaZero_skips() {
        given(quotaGuard.getRemainingDailyQuota()).willReturn(0);

        jobScheduler.schedulePendingJobs();

        verify(jobRepository, never()).lockPending(any(), anyInt());
    }

    @Test
    @DisplayName("정상 처리: 3단 게이트 번역 완료 후 OutboxEvent를 생성하고 잡을 COMPLETED로 전이한다")
    void processJob_success() {
        given(translationGateService.translateBatch(eq("ko"), eq("ja"), anyList()))
                .willReturn(List.of(new TranslatedItem("RESTAURANT:12", "路地食堂", "老舗")));

        jobScheduler.processJob(sampleJob);

        assertThat(sampleJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        verify(jobRepository).save(sampleJob);
    }

    @Test
    @DisplayName("번역 중 쿼터 소진 예외 발생 시 retryCount 증가 없이 잡을 연기한다")
    void processJob_quotaExceeded_delaysJobWithoutIncrementingRetryCount() {
        given(translationGateService.translateBatch(eq("ko"), eq("ja"), anyList()))
                .willThrow(new BusinessException(ErrorCode.TRANSLATION_QUOTA_EXCEEDED));

        jobScheduler.processJob(sampleJob);

        assertThat(sampleJob.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(sampleJob.getRetryCount()).isEqualTo(0);
        assertThat(sampleJob.getNextRetryAt()).isAfter(LocalDateTime.now());
        verify(outboxEventRepository, never()).save(any());
        verify(jobRepository).save(sampleJob);
    }

    @Test
    @DisplayName("재시도 소진(3회차 실패) 시 잡을 FAILED로 전이하고 DLT 토픽으로 발행한다")
    void processJob_retryExhausted_isolatesToDlt() {
        TranslationJob thirdAttemptJob = TranslationJob.builder()
                .id(1L)
                .eventId("event-1")
                .restaurantId(12L)
                .targetLocale("ja")
                .sourceLocale("ko")
                .entries("[{\"targetType\":\"RESTAURANT\",\"targetId\":12,\"name\":\"골목식당\",\"description\":null}]")
                .status(JobStatus.IN_PROGRESS)
                .retryCount(2)
                .nextRetryAt(LocalDateTime.now())
                .build();

        given(translationGateService.translateBatch(any(), any(), any()))
                .willThrow(new RuntimeException("AI API connection failed"));

        jobScheduler.processJob(thirdAttemptJob);

        assertThat(thirdAttemptJob.getStatus()).isEqualTo(JobStatus.FAILED);
        verify(kafkaTemplate).send(eq("translation-requests.DLT"), eq("restaurant:12"), anyString());
        verify(jobRepository).save(thirdAttemptJob);
    }
}
