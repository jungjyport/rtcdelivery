package com.rtcdelivery.translation.dto.response;

import com.rtcdelivery.translation.domain.UgcContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UgcTranslationResponse {

    @Schema(description = "콘텐츠 유형", example = "REVIEW")
    private UgcContentType contentType;

    @Schema(description = "콘텐츠 ID", example = "1024")
    private Long contentId;

    @Schema(description = "원문 언어", example = "ko")
    private String sourceLocale;

    @Schema(description = "적용된 대상 언어", example = "ja")
    private String targetLocale;

    @Schema(description = "번역 결과 문자열", example = "麺がもちもちで、スープが濃厚です。再訪希望100%！")
    private String translatedText;

    @Schema(description = "캐시 또는 원문 재사용 여부", example = "true")
    private boolean cached;
}
