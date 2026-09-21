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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TranslationGateServiceTest {

    @Mock
    private GlossaryRepository glossaryRepository;

    @Mock
    private TranslationHistoryRepository historyRepository;

    @Mock
    private TranslationProvider translationProvider;

    @Mock
    private QuotaGuard quotaGuard;

    @InjectMocks
    private TranslationGateService translationGateService;

    @Test
    @DisplayName("1단 게이트(Glossary) 히트 시 Provider를 호출하지 않고 사전문구를 반환한다")
    void translateBatch_glossaryHit_skipsAi() {
        Glossary glossary = Glossary.builder()
                .sourceText("김치찌개")
                .sourceLocale("ko")
                .targetLocale("ja")
                .translatedText("キムチチゲ")
                .isActive(true)
                .build();

        given(glossaryRepository.findBySourceTextAndSourceLocaleAndTargetLocaleAndIsActiveTrue(
                "김치찌개", "ko", "ja")).willReturn(Optional.of(glossary));

        ItemToTranslate item = new ItemToTranslate("MENU:1", "김치찌개", null);
        List<TranslatedItem> result = translationGateService.translateBatch("ko", "ja", List.of(item));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("キムチチゲ");
        verify(translationProvider, never()).translate(any());
    }

    @Test
    @DisplayName("2단 게이트(TranslationHistory) 히트 시 Provider를 호출하지 않고 이력을 재사용한다")
    void translateBatch_historyHit_skipsAi() {
        String hash = TranslationHistory.calculateHash("된장찌개");
        TranslationHistory history = TranslationHistory.builder()
                .sourceHash(hash)
                .sourceLocale("ko")
                .targetLocale("ja")
                .translatedText("テンジャンチゲ")
                .build();

        given(glossaryRepository.findBySourceTextAndSourceLocaleAndTargetLocaleAndIsActiveTrue(
                "된장찌개", "ko", "ja")).willReturn(Optional.empty());
        given(historyRepository.findBySourceHashAndSourceLocaleAndTargetLocale(hash, "ko", "ja"))
                .willReturn(Optional.of(history));

        ItemToTranslate item = new ItemToTranslate("MENU:2", "된장찌개", null);
        List<TranslatedItem> result = translationGateService.translateBatch("ko", "ja", List.of(item));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("テンジャンチゲ");
        verify(translationProvider, never()).translate(any());
    }

    @Test
    @DisplayName("사전/이력 모두 미적용 시 3단 Gemini를 호출하고 결과를 이력에 저장한다")
    void translateBatch_callsAiAndSavesHistory() {
        given(glossaryRepository.findBySourceTextAndSourceLocaleAndTargetLocaleAndIsActiveTrue(any(), eq("ko"), eq("ja")))
                .willReturn(Optional.empty());
        given(historyRepository.findBySourceHashAndSourceLocaleAndTargetLocale(any(), eq("ko"), eq("ja")))
                .willReturn(Optional.empty());
        given(quotaGuard.canCallGemini()).willReturn(true);

        TranslationBatchResult aiResult = new TranslationBatchResult(
                List.of(new TranslatedItem("MENU:3", "ケランマリ", "美味しい卵焼き")),
                "gemini-3.5-flash-lite", 10, 20
        );
        given(translationProvider.translate(any(TranslationBatchRequest.class))).willReturn(aiResult);

        ItemToTranslate item = new ItemToTranslate("MENU:3", "계란말이", "맛있는 계란말이");
        List<TranslatedItem> result = translationGateService.translateBatch("ko", "ja", List.of(item));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("ケランマリ");
        assertThat(result.get(0).description()).isEqualTo("美味しい卵焼き");

        verify(quotaGuard).recordGeminiCall();
        verify(historyRepository, org.mockito.Mockito.times(2)).save(any(TranslationHistory.class));
    }

    @Test
    @DisplayName("AI 호출 전 쿼터 가드가 차단하면 TRANSLATION_QUOTA_EXCEEDED 예외를 던진다")
    void translateBatch_quotaBlocked_throwsException() {
        given(glossaryRepository.findBySourceTextAndSourceLocaleAndTargetLocaleAndIsActiveTrue(any(), eq("ko"), eq("ja")))
                .willReturn(Optional.empty());
        given(historyRepository.findBySourceHashAndSourceLocaleAndTargetLocale(any(), eq("ko"), eq("ja")))
                .willReturn(Optional.empty());
        given(quotaGuard.canCallGemini()).willReturn(false);

        ItemToTranslate item = new ItemToTranslate("MENU:4", "새메뉴", null);

        assertThatThrownBy(() -> translationGateService.translateBatch("ko", "ja", List.of(item)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSLATION_QUOTA_EXCEEDED);
    }
}
