package com.rtcdelivery.foodcatalog.dto.response;

import com.rtcdelivery.foodcatalog.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리 응답 DTO")
public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "1") Long id,

        @Schema(description = "프론트엔드 i18n 키 접미사. `$t('category.' + code)`로 표출한다",
                example = "korean")
        String code,

        @Schema(description = "관리용 한국어 이름. 사용자 노출에는 쓰지 않는다", example = "한식")
        String name,

        @Schema(description = "카테고리 이미지 URL") String imageUrl,

        @Schema(description = "정렬 순서", example = "1") int displayOrder
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getImageUrl(),
                category.getDisplayOrder()
        );
    }
}
