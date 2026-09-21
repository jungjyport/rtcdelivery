package com.rtcdelivery.translation.service;

import com.rtcdelivery.translation.domain.TranslationHistory;
import com.rtcdelivery.translation.domain.UgcTranslation;
import com.rtcdelivery.translation.dto.request.UgcTranslationRequest;
import com.rtcdelivery.translation.dto.response.UgcTranslationResponse;
import com.rtcdelivery.translation.exception.BusinessException;
import com.rtcdelivery.translation.exception.ErrorCode;
import com.rtcdelivery.translation.repository.UgcTranslationRepository;
import com.rtcdelivery.translation.security.Actor;
import com.rtcdelivery.translation.service.TranslationProvider.ItemToTranslate;
import com.rtcdelivery.translation.service.TranslationProvider.TranslatedItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UgcTranslationService {

    private final UgcTranslationRepository ugcTranslationRepository;
    private final TranslationGateService translationGateService;
    private final QuotaGuard quotaGuard;

    @Transactional
    public UgcTranslationResponse translateUgc(UgcTranslationRequest request,
                                              String targetLocale,
                                              Actor actor) {
        String sourceLocale = request.resolveSourceLocale();

        // 1. sourceLocale == targetLocale 이면 AI 호출 없이 원문 반환
        if (sourceLocale.equalsIgnoreCase(targetLocale)) {
            log.debug("Source and target locales match ({}). Returning original text.", sourceLocale);
            return UgcTranslationResponse.builder()
                    .contentType(request.getContentType())
                    .contentId(request.getContentId())
                    .sourceLocale(sourceLocale)
                    .targetLocale(targetLocale)
                    .translatedText(request.getText())
                    .cached(true)
                    .build();
        }

        // 2. 원문 해시 계산 및 ugc_translation 테이블 조회
        String sourceHash = TranslationHistory.calculateHash(request.getText());
        Optional<UgcTranslation> existingOpt = ugcTranslationRepository
                .findByContentTypeAndContentIdAndSourceHashAndTargetLocale(
                        request.getContentType(), request.getContentId(), sourceHash, targetLocale
                );

        if (existingOpt.isPresent()) {
            log.debug("ugc_translation cache hit: contentId={}, hash={}", request.getContentId(), sourceHash);
            return UgcTranslationResponse.builder()
                    .contentType(request.getContentType())
                    .contentId(request.getContentId())
                    .sourceLocale(sourceLocale)
                    .targetLocale(targetLocale)
                    .translatedText(existingOpt.get().getTranslatedText())
                    .cached(true)
                    .build();
        }

        // 3. 사용자별 일일 상한 검사 (캐시 히트는 미차감)
        if (actor != null && actor.memberId() != null) {
            boolean quotaAvailable = quotaGuard.checkAndRecordUserUgcQuota(actor.memberId());
            if (!quotaAvailable) {
                log.warn("User daily UGC translation limit exceeded: userId={}", actor.memberId());
                throw new BusinessException(ErrorCode.TRANSLATION_QUOTA_EXCEEDED);
            }
        }

        // 4. 3단 게이트 경유 번역 (entries 1건)
        String ref = request.getContentType().name() + ":" + request.getContentId();
        ItemToTranslate item = new ItemToTranslate(ref, request.getText(), null);

        List<TranslatedItem> result = translationGateService.translateBatch(sourceLocale, targetLocale, List.of(item));
        if (result.isEmpty() || result.get(0).name() == null) {
            throw new BusinessException(ErrorCode.TRANSLATION_UNAVAILABLE, "Translation result empty");
        }

        String translatedText = result.get(0).name();

        // 5. ugc_translation INSERT
        UgcTranslation ugcTranslation = UgcTranslation.builder()
                .contentType(request.getContentType())
                .contentId(request.getContentId())
                .sourceHash(sourceHash)
                .sourceLocale(sourceLocale)
                .targetLocale(targetLocale)
                .translatedText(translatedText)
                .provider("GEMINI")
                .model("gemini-3.5-flash-lite")
                .build();

        try {
            ugcTranslationRepository.save(ugcTranslation);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent insertion for ugc_translation: contentId={}, hash={}", request.getContentId(), sourceHash);
        }

        return UgcTranslationResponse.builder()
                .contentType(request.getContentType())
                .contentId(request.getContentId())
                .sourceLocale(sourceLocale)
                .targetLocale(targetLocale)
                .translatedText(translatedText)
                .cached(false)
                .build();
    }
}
