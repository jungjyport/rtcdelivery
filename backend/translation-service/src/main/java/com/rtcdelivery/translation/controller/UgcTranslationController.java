package com.rtcdelivery.translation.controller;

import com.rtcdelivery.translation.common.ApiResponse;
import com.rtcdelivery.translation.common.SupportedLocale;
import com.rtcdelivery.translation.dto.request.UgcTranslationRequest;
import com.rtcdelivery.translation.dto.response.UgcTranslationResponse;
import com.rtcdelivery.translation.exception.BusinessException;
import com.rtcdelivery.translation.exception.ErrorCode;
import com.rtcdelivery.translation.security.Actor;
import com.rtcdelivery.translation.service.UgcTranslationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Translations", description = "번역 관련 API")
@RestController
@RequestMapping("/api/v1/translations")
@RequiredArgsConstructor
public class UgcTranslationController {

    private final UgcTranslationService ugcTranslationService;

    @Operation(summary = "UGC 온디맨드 번역", description = "리뷰 등 사용자 생성 콘텐츠(UGC)를 대상 언어로 실시간 번역합니다.")
    @PostMapping("/ugc")
    public ApiResponse<UgcTranslationResponse> translateUgc(
            @Valid @RequestBody UgcTranslationRequest request,
            Authentication authentication) {

        Actor actor = Actor.from(authentication);
        if (actor.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String targetLocale = SupportedLocale.current();
        UgcTranslationResponse response = ugcTranslationService.translateUgc(request, targetLocale, actor);

        return ApiResponse.success(response);
    }
}
