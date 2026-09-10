package com.rtcdelivery.foodcatalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부분 수정({@code PATCH}) 요청. {@code null}인 필드는 변경하지 않는다.
 *
 * <p>{@code code}는 프론트 i18n 키와의 계약이라 변경 대상에서 제외한다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "카테고리 수정 요청 DTO")
public class CategoryUpdateRequest {

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
