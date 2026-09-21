package com.rtcdelivery.foodcatalog.dto.event;

import java.util.List;

public record TranslationRequestPayload(
        String sourceLocale,
        List<String> targetLocales,
        String reason,
        List<RequestEntry> entries
) {
    public record RequestEntry(
            String targetType,
            Long targetId,
            String name,
            String description
    ) {}
}
