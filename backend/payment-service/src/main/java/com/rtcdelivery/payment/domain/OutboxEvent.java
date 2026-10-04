package com.rtcdelivery.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "outbox_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_outbox_event_id", columnNames = {"event_id"})
        },
        indexes = {
                @Index(name = "idx_payment_outbox_poll", columnList = "status, next_retry_at")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OutboxEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.lastError = abbreviate(error);
    }

    public void markRetry(LocalDateTime nextRetryAt, String error) {
        this.status = OutboxStatus.PENDING;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.lastError = abbreviate(error);
    }

    private static String abbreviate(String str) {
        if (str == null) return null;
        return str.length() > 500 ? str.substring(0, 500) : str;
    }
}
