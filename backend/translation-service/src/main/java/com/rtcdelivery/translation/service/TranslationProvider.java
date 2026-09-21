package com.rtcdelivery.translation.service;

import java.util.List;

public interface TranslationProvider {

    TranslationBatchResult translate(TranslationBatchRequest request);

    record ItemToTranslate(String ref, String name, String description) {}

    record TranslatedItem(String ref, String name, String description) {}

    record TranslationBatchRequest(String sourceLocale, String targetLocale, List<ItemToTranslate> items) {}

    record TranslationBatchResult(
            List<TranslatedItem> items,
            String model,
            Integer inputTokens,
            Integer outputTokens
    ) {}
}
