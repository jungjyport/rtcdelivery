package com.rtcdelivery.foodcatalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "카테고리 생성 요청 DTO")
public class CategoryCreateRequest {

    @NotBlank(message = "카테고리 코드는 필수입니다.")
    @Size(max = 50, message = "카테고리 코드는 최대 50자입니다.")
    @Pattern(regexp = "^[a-z][a-zA-Z0-9]*$",
            message = "카테고리 코드는 소문자로 시작하는 camelCase여야 합니다.")
    @Schema(description = "프론트엔드 i18n 키 접미사. `i18n/locales/{ko,ja}.json`의 "
            + "`category.*`에 같은 키를 함께 추가해야 한다", example = "korean")
    private String code;

    @NotBlank(message = "카테고리 이름은 필수입니다.")
    @Size(max = 50, message = "카테고리 이름은 최대 50자입니다.")
    @Schema(description = "관리용 한국어 이름", example = "한식")
    private String name;

    @Size(max = 500, message = "이미지 URL은 최대 500자입니다.")
    @Schema(description = "카테고리 이미지 URL")
    private String imageUrl;

    @Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
    @Schema(description = "정렬 순서", example = "1")
    private Integer displayOrder;
}
