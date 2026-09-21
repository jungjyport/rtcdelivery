package com.rtcdelivery.translation.service;

import com.rtcdelivery.translation.domain.UgcContentType;
import com.rtcdelivery.translation.domain.UgcTranslation;
import com.rtcdelivery.translation.dto.request.UgcTranslationRequest;
import com.rtcdelivery.translation.dto.response.UgcTranslationResponse;
import com.rtcdelivery.translation.exception.BusinessException;
import com.rtcdelivery.translation.exception.ErrorCode;
import com.rtcdelivery.translation.repository.UgcTranslationRepository;
import com.rtcdelivery.translation.security.Actor;
import com.rtcdelivery.translation.service.TranslationProvider.TranslatedItem;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UgcTranslationServiceTest {

    @Mock
    private UgcTranslationRepository ugcTranslationRepository;

    @Mock
    private TranslationGateService translationGateService;

    @Mock
    private QuotaGuard quotaGuard;

    @InjectMocks
    private UgcTranslationService ugcTranslationService;

    private final Actor actor = new Actor(100L, false);

    @Test
    @DisplayName("sourceLocale == targetLocale 이면 AI를 호출하지 않고 원문을 반환한다 (cached: true)")
    void translateUgc_sameLocale_returnsOriginal() {
        UgcTranslationRequest request = UgcTranslationRequest.builder()
                .contentType(UgcContentType.REVIEW)
                .contentId(1L)
                .text("정말 맛있어요")
                .sourceLocale("ko")
                .build();

        UgcTranslationResponse response = ugcTranslationService.translateUgc(request, "ko", actor);

        assertThat(response.getTranslatedText()).isEqualTo("정말 맛있어요");
        assertThat(response.isCached()).isTrue();
        verify(translationGateService, never()).translateBatch(any(), any(), any());
    }

    @Test
    @DisplayName("ugc_translation 캐시 히트 시 AI를 호출하지 않고 사용자 쿼터도 차감하지 않는다")
    void translateUgc_cacheHit_returnsCachedText() {
        UgcTranslation existing = UgcTranslation.builder()
                .contentType(UgcContentType.REVIEW)
                .contentId(1L)
                .sourceHash("hash-val")
                .sourceLocale("ko")
                .targetLocale("ja")
                .translatedText("とても美味しいです")
                .build();

        given(ugcTranslationRepository.findByContentTypeAndContentIdAndSourceHashAndTargetLocale(
                eq(UgcContentType.REVIEW), eq(1L), any(), eq("ja")
        )).willReturn(Optional.of(existing));

        UgcTranslationRequest request = UgcTranslationRequest.builder()
                .contentType(UgcContentType.REVIEW)
                .contentId(1L)
                .text("정말 맛있어요")
                .sourceLocale("ko")
                .build();

        UgcTranslationResponse response = ugcTranslationService.translateUgc(request, "ja", actor);

        assertThat(response.getTranslatedText()).isEqualTo("とても美味しいです");
        assertThat(response.isCached()).isTrue();
        verify(quotaGuard, never()).checkAndRecordUserUgcQuota(anyLong());
        verify(translationGateService, never()).translateBatch(any(), any(), any());
    }

    @Test
    @DisplayName("사용자 UGC 일일 상한 초과 시 429 TRANSLATION_QUOTA_EXCEEDED 예외를 던진다")
    void translateUgc_userQuotaExceeded_throwsException() {
        given(ugcTranslationRepository.findByContentTypeAndContentIdAndSourceHashAndTargetLocale(
                any(), anyLong(), any(), eq("ja")
        )).willReturn(Optional.empty());
        given(quotaGuard.checkAndRecordUserUgcQuota(100L)).willReturn(false);

        UgcTranslationRequest request = UgcTranslationRequest.builder()
                .contentType(UgcContentType.REVIEW)
                .contentId(1L)
                .text("정말 맛있어요")
                .sourceLocale("ko")
                .build();

        assertThatThrownBy(() -> ugcTranslationService.translateUgc(request, "ja", actor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSLATION_QUOTA_EXCEEDED);
    }

    @Test
    @DisplayName("정상 번역: 게이트 서비스를 호출하고 ugc_translation에 저장 후 결과를 반환한다")
    void translateUgc_success() {
        given(ugcTranslationRepository.findByContentTypeAndContentIdAndSourceHashAndTargetLocale(
                any(), anyLong(), any(), eq("ja")
        )).willReturn(Optional.empty());
        given(quotaGuard.checkAndRecordUserUgcQuota(100L)).willReturn(true);
        given(translationGateService.translateBatch(eq("ko"), eq("ja"), anyList()))
                .willReturn(List.of(new TranslatedItem("REVIEW:1", "とても美味しいです", null)));

        UgcTranslationRequest request = UgcTranslationRequest.builder()
                .contentType(UgcContentType.REVIEW)
                .contentId(1L)
                .text("정말 맛있어요")
                .sourceLocale("ko")
                .build();

        UgcTranslationResponse response = ugcTranslationService.translateUgc(request, "ja", actor);

        assertThat(response.getTranslatedText()).isEqualTo("とても美味しいです");
        assertThat(response.isCached()).isFalse();
        verify(ugcTranslationRepository).save(any(UgcTranslation.class));
    }
}
