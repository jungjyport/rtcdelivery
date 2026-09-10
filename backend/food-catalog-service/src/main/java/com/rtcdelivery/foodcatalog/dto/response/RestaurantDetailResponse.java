package com.rtcdelivery.foodcatalog.dto.response;

import com.rtcdelivery.foodcatalog.domain.Food;
import com.rtcdelivery.foodcatalog.domain.Restaurant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "음식점 상세 응답 DTO — 음식점 정보와 메뉴 목록을 함께 내려준다")
public record RestaurantDetailResponse(
        @Schema(description = "음식점 정보") RestaurantResponse restaurant,
        @Schema(description = "메뉴 목록") List<FoodResponse> foods
) {

    public static RestaurantDetailResponse of(Restaurant restaurant, List<Food> foods, String locale) {
        return new RestaurantDetailResponse(
                RestaurantResponse.of(restaurant, locale),
                foods.stream().map(food -> FoodResponse.of(food, locale)).toList()
        );
    }
}
