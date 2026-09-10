package com.rtcdelivery.foodcatalog.dto.response;

import com.rtcdelivery.foodcatalog.domain.Food;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메뉴 응답 DTO")
public record FoodResponse(
        @Schema(description = "메뉴 ID", example = "10") Long id,

        @Schema(description = "소속 음식점 ID", example = "1") Long restaurantId,

        @Schema(description = "메뉴명. 요청 locale의 번역본이며, 번역이 없으면 원본(ko)",
                example = "김치찌개")
        String name,

        @Schema(description = "메뉴 설명. 번역 규칙은 name과 같다") String description,

        @Schema(description = "가격 (원)", example = "9000") int price,

        @Schema(description = "메뉴 이미지 URL") String imageUrl,

        @Schema(description = "품절 여부", example = "false") boolean soldOut,

        @Schema(description = "정렬 순서", example = "1") int displayOrder,

        @Schema(description = "응답에 적용된 locale", example = "ko") String locale
) {

    public static FoodResponse of(Food food, String locale) {
        return new FoodResponse(
                food.getId(),
                food.getRestaurant().getId(),
                food.resolveName(locale),
                food.resolveDescription(locale),
                food.getPrice(),
                food.getImageUrl(),
                food.isSoldOut(),
                food.getDisplayOrder(),
                locale
        );
    }
}
