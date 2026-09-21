package com.rtcdelivery.translation.dto.response;

import com.rtcdelivery.translation.domain.TranslationJob;

import java.time.LocalDateTime;

public record TranslationJobAdminResponse(
        Long id,
        String eventId,
        Long restaurantId,
        String sourceLocale,
        String targetLocale,
        String status,
        int retryCount,
        LocalDateTime nextRetryAt,
        String lastError,
        String entries,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TranslationJobAdminResponse from(TranslationJob job) {
        return new TranslationJobAdminResponse(
                job.getId(),
                job.getEventId(),
                job.getRestaurantId(),
                job.getSourceLocale(),
                job.getTargetLocale(),
                job.getStatus().name(),
                job.getRetryCount(),
                job.getNextRetryAt(),
                job.getLastError(),
                job.getEntries(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
