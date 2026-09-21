package com.rtcdelivery.translation.dto.event;

import java.util.List;

public record TranslationResultPayload(
        String targetLocale,
        String sourceLocale,
        List<ResultEntry> entries
) {
    public record ResultEntry(
            String targetType,
            Long targetId,
            String name,
            String description
    ) {}
}
