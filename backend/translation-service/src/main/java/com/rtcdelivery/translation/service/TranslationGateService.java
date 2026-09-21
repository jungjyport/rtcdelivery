package com.rtcdelivery.translation.service;

import com.rtcdelivery.translation.domain.Glossary;
import com.rtcdelivery.translation.domain.TranslationHistory;
import com.rtcdelivery.translation.exception.BusinessException;
import com.rtcdelivery.translation.exception.ErrorCode;
import com.rtcdelivery.translation.repository.GlossaryRepository;
import com.rtcdelivery.translation.repository.TranslationHistoryRepository;
import com.rtcdelivery.translation.service.TranslationProvider.ItemToTranslate;
import com.rtcdelivery.translation.service.TranslationProvider.TranslatedItem;
import com.rtcdelivery.translation.service.TranslationProvider.TranslationBatchRequest;
import com.rtcdelivery.translation.service.TranslationProvider.TranslationBatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationGateService {

    private final GlossaryRepository glossaryRepository;
    private final TranslationHistoryRepository historyRepository;
    private final TranslationProvider translationProvider;
    private final QuotaGuard quotaGuard;

    @Transactional
    public List<TranslatedItem> translateBatch(String sourceLocale,
                                               String targetLocale,
                                               List<ItemToTranslate> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Map<String, String> resolvedNames = new HashMap<>();
        Map<String, String> resolvedDescriptions = new HashMap<>();
        List<ItemToTranslate> itemsForAi = new ArrayList<>();

        // 1단(Glossary) & 2단(TranslationHistory) 게이트 적용
        for (ItemToTranslate item : items) {
            String nameTrans = resolveFromGlossaryOrHistory(item.name(), sourceLocale, targetLocale);
            if (nameTrans != null) {
                resolvedNames.put(item.ref(), nameTrans);
            }

            String descTrans = null;
            if (item.description() == null || item.description().isBlank()) {
                descTrans = null;
                resolvedDescriptions.put(item.ref(), null);
            } else {
                descTrans = resolveFromGlossaryOrHistory(item.description(), sourceLocale, targetLocale);
                if (descTrans != null) {
                    resolvedDescriptions.put(item.ref(), descTrans);
                }
            }

            // name과 description 모두 사전/이력에서 해결되지 않은 경우 AI 호출 대상에 포함
            boolean needName = (nameTrans == null);
            boolean needDesc = (item.description() != null && !item.description().isBlank() && descTrans == null);

            if (needName || needDesc) {
                itemsForAi.add(item);
            }
        }

        log.debug("3-Stage Gate summary: total={}, resolvedWithoutAi={}, sentToAi={}",
                items.size(), items.size() - itemsForAi.size(), itemsForAi.size());

        // 3단: 남은 항목 AI 호출
        if (!itemsForAi.isEmpty()) {
            if (!quotaGuard.canCallGemini()) {
                log.warn("Gemini call blocked by local quota guard");
                throw new BusinessException(ErrorCode.TRANSLATION_QUOTA_EXCEEDED);
            }

            quotaGuard.recordGeminiCall();

            TranslationBatchResult aiResult;
            try {
                aiResult = translationProvider.translate(
                        new TranslationBatchRequest(sourceLocale, targetLocale, itemsForAi)
                );
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.TRANSLATION_QUOTA_EXCEEDED) {
                    quotaGuard.forceConsumeDailyQuota();
                }
                throw e;
            }

            for (TranslatedItem translated : aiResult.items()) {
                resolvedNames.put(translated.ref(), translated.name());
                resolvedDescriptions.put(translated.ref(), translated.description());

                // 원본 항목 매칭 후 TranslationHistory에 저장
                ItemToTranslate original = itemsForAi.stream()
                        .filter(i -> i.ref().equals(translated.ref()))
                        .findFirst()
                        .orElse(null);

                if (original != null) {
                    saveHistory(original.name(), translated.name(), sourceLocale, targetLocale,
                            "GEMINI", aiResult.model(), aiResult.inputTokens(), aiResult.outputTokens());

                    if (original.description() != null && !original.description().isBlank()
                            && translated.description() != null) {
                        saveHistory(original.description(), translated.description(), sourceLocale, targetLocale,
                                "GEMINI", aiResult.model(), aiResult.inputTokens(), aiResult.outputTokens());
                    }
                }
            }
        }

        List<TranslatedItem> finalResult = new ArrayList<>();
        for (ItemToTranslate item : items) {
            String name = resolvedNames.get(item.ref());
            String desc = resolvedDescriptions.get(item.ref());
            finalResult.add(new TranslatedItem(item.ref(), name, desc));
        }

        return finalResult;
    }

    private String resolveFromGlossaryOrHistory(String text, String sourceLocale, String targetLocale) {
        if (text == null || text.isBlank()) return null;
        String normalized = TranslationHistory.normalize(text);

        // 1단: Glossary (완전일치)
        Optional<Glossary> glossaryOpt = glossaryRepository
                .findBySourceTextAndSourceLocaleAndTargetLocaleAndIsActiveTrue(normalized, sourceLocale, targetLocale);
        if (glossaryOpt.isPresent()) {
            log.debug("Glossary hit: '{}' -> '{}'", text, glossaryOpt.get().getTranslatedText());
            return glossaryOpt.get().getTranslatedText();
        }

        // 2단: TranslationHistory (원문 해시)
        String hash = TranslationHistory.calculateHash(text);
        Optional<TranslationHistory> historyOpt = historyRepository
                .findBySourceHashAndSourceLocaleAndTargetLocale(hash, sourceLocale, targetLocale);
        if (historyOpt.isPresent()) {
            log.debug("TranslationHistory hit: '{}' -> '{}'", text, historyOpt.get().getTranslatedText());
            return historyOpt.get().getTranslatedText();
        }

        return null;
    }

    private void saveHistory(String sourceText, String translatedText,
                             String sourceLocale, String targetLocale,
                             String provider, String model,
                             Integer inputTokens, Integer outputTokens) {
        if (sourceText == null || translatedText == null) return;
        String hash = TranslationHistory.calculateHash(sourceText);

        if (historyRepository.findBySourceHashAndSourceLocaleAndTargetLocale(hash, sourceLocale, targetLocale).isPresent()) {
            return;
        }

        TranslationHistory history = TranslationHistory.builder()
                .sourceHash(hash)
                .sourceLocale(sourceLocale)
                .targetLocale(targetLocale)
                .sourceText(sourceText)
                .translatedText(translatedText)
                .provider(provider)
                .model(model)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .build();

        try {
            historyRepository.save(history);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent insertion for translation history: hash={}", hash);
        }
    }
}
