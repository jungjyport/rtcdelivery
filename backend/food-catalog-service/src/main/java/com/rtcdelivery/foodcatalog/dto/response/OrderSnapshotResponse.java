package com.rtcdelivery.foodcatalog.dto.response;

import com.rtcdelivery.foodcatalog.domain.Food;
import com.rtcdelivery.foodcatalog.domain.Restaurant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "주문 시점 카탈로그 스냅샷 응답 (내부 서비스 전용)")
public record OrderSnapshotResponse(
        @Schema(description = "음식점 ID", example = "1")
        Long restaurantId,

        @Schema(description = "음식점 소유 점주 ID", example = "10")
        Long ownerId,

        @Schema(description = "영업 여부 (비활성 시 false)", example = "true")
        boolean active,

        @Schema(description = "음식점 원본명(ko)", example = "서울 김치찌개")
        String name,

        @Schema(description = "배달비", example = "3000")
        int deliveryFee,

        @Schema(description = "최소 주문 금액", example = "12000")
        int minOrderAmount,

        @Schema(description = "메뉴 목록")
        List<FoodSnapshotResponse> foods
) {

    @Schema(description = "메뉴 스냅샷 항목")
    public record FoodSnapshotResponse(
            @Schema(description = "메뉴 ID", example = "10")
            Long foodId,

            @Schema(description = "메뉴 원본명(ko)", example = "김치찌개")
            String name,

            @Schema(description = "가격", example = "9000")
            int price,

            @Schema(description = "품절 여부", example = "false")
            boolean soldOut
    ) {
        public static FoodSnapshotResponse from(Food food) {
            return new FoodSnapshotResponse(
                    food.getId(),
                    food.getName(),
                    food.getPrice(),
                    food.isSoldOut()
            );
        }
    }

    public static OrderSnapshotResponse of(Restaurant restaurant, List<Food> foods) {
        return new OrderSnapshotResponse(
                restaurant.getId(),
                restaurant.getOwnerId(),
                restaurant.isActive(),
                restaurant.getName(),
                restaurant.getDeliveryFee(),
                restaurant.getMinOrderAmount(),
                foods.stream().map(FoodSnapshotResponse::from).toList()
        );
    }
}
