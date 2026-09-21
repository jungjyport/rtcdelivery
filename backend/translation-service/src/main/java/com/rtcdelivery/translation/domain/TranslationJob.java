package com.rtcdelivery.translation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "translation_job",
        indexes = {
                @Index(name = "idx_job_poll", columnList = "status, next_retry_at"),
                @Index(name = "idx_job_event", columnList = "event_id"),
                @Index(name = "idx_job_restaurant", columnList = "restaurant_id")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TranslationJob extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "target_locale", nullable = false, length = 10)
    private String targetLocale;

    @Column(name = "source_locale", nullable = false, length = 10)
    private String sourceLocale;

    @Column(name = "entries", nullable = false, columnDefinition = "JSON")
    private String entries;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public void markInProgress() {
        this.status = JobStatus.IN_PROGRESS;
    }

    public void markCompleted() {
        this.status = JobStatus.COMPLETED;
    }

    public void markSuperseded() {
        this.status = JobStatus.SUPERSEDED;
    }

    public void markQuotaDelayed(LocalDateTime nextRetryAt) {
        this.status = JobStatus.PENDING;
        this.nextRetryAt = nextRetryAt;
        // 쿼터 소진은 재시도 횟수를 증가시키지 않음
    }

    public void markFailed(String error) {
        this.status = JobStatus.FAILED;
        this.lastError = abbreviate(error);
    }

    public void markRetry(LocalDateTime nextRetryAt, String error) {
        this.status = JobStatus.PENDING;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.lastError = abbreviate(error);
    }

    public void resetForRetry() {
        this.status = JobStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now();
    }

    private static String abbreviate(String str) {
        if (str == null) return null;
        return str.length() > 500 ? str.substring(0, 500) : str;
    }
}
