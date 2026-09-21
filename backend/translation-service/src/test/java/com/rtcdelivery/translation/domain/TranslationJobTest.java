package com.rtcdelivery.translation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationJobTest {

    @Test
    @DisplayName("상태 전이: PENDING -> IN_PROGRESS -> COMPLETED")
    void stateTransitions_success() {
        TranslationJob job = TranslationJob.builder()
                .eventId("event-123")
                .restaurantId(1L)
                .targetLocale("ja")
                .sourceLocale("ko")
                .entries("[]")
                .status(JobStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);

        job.markInProgress();
        assertThat(job.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);

        job.markCompleted();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    @DisplayName("상태 전이: PENDING -> SUPERSEDED")
    void stateTransition_superseded() {
        TranslationJob job = TranslationJob.builder()
                .eventId("event-123")
                .restaurantId(1L)
                .targetLocale("ja")
                .sourceLocale("ko")
                .entries("[]")
                .status(JobStatus.PENDING)
                .nextRetryAt(LocalDateTime.now())
                .build();

        job.markSuperseded();
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("쿼터 지연 시 상태는 PENDING 유지되고 retryCount는 증가하지 않는다")
    void markQuotaDelayed_doesNotIncrementRetryCount() {
        LocalDateTime now = LocalDateTime.now();
        TranslationJob job = TranslationJob.builder()
                .eventId("event-123")
                .restaurantId(1L)
                .targetLocale("ja")
                .sourceLocale("ko")
                .entries("[]")
                .status(JobStatus.IN_PROGRESS)
                .retryCount(1)
                .nextRetryAt(now)
                .build();

        LocalDateTime tomorrow = now.plusDays(1);
        job.markQuotaDelayed(tomorrow);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getNextRetryAt()).isEqualTo(tomorrow);
    }

    @Test
    @DisplayName("재시도 시 retryCount가 1 증가하고 nextRetryAt과 lastError가 갱신된다")
    void markRetry_incrementsRetryCountAndUpdatesError() {
        TranslationJob job = TranslationJob.builder()
                .eventId("event-123")
                .restaurantId(1L)
                .targetLocale("ja")
                .sourceLocale("ko")
                .entries("[]")
                .status(JobStatus.IN_PROGRESS)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        LocalDateTime next = LocalDateTime.now().plusSeconds(2);
        job.markRetry(next, "Connection timeout");

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getNextRetryAt()).isEqualTo(next);
        assertThat(job.getLastError()).isEqualTo("Connection timeout");
    }

    @Test
    @DisplayName("markFailed 호출 시 FAILED로 전이된다")
    void markFailed() {
        TranslationJob job = TranslationJob.builder()
                .eventId("event-123")
                .restaurantId(1L)
                .targetLocale("ja")
                .sourceLocale("ko")
                .entries("[]")
                .status(JobStatus.IN_PROGRESS)
                .retryCount(3)
                .nextRetryAt(LocalDateTime.now())
                .build();

        job.markFailed("Fatal error");
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getLastError()).isEqualTo("Fatal error");
    }
}
