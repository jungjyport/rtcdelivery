package com.rtcdelivery.translation.dto.request;

import com.rtcdelivery.translation.domain.UgcContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UgcTranslationRequest {

    @Schema(description = "콘텐츠 유형", example = "REVIEW")
    @NotNull(message = "contentType은 필수입니다.")
    private UgcContentType contentType;

    @Schema(description = "콘텐츠 ID", example = "1024")
    @NotNull(message = "contentId는 필수입니다.")
    @Positive(message = "contentId는 양수여야 합니다.")
    private Long contentId;

    @Schema(description = "번역할 원문 텍스트 (최대 2000자)", example = "면이 쫄깃하고 국물이 진해요. 재방문 의사 100%!")
    @NotBlank(message = "text는 비어 있을 수 없습니다.")
    @Size(max = 2000, message = "text는 최대 2000자까지 허용됩니다.")
    private String text;

    @Schema(description = "원문 언어 (기본값: ko)", example = "ko")
    private String sourceLocale;

    public String resolveSourceLocale() {
        return (sourceLocale != null && !sourceLocale.isBlank()) ? sourceLocale.trim() : "ko";
    }
}
