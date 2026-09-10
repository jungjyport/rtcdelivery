package com.rtcdelivery.foodcatalog.dto.response;

import com.rtcdelivery.foodcatalog.domain.Restaurant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "음식점 응답 DTO")
public record RestaurantResponse(
        @Schema(description = "음식점 ID", example = "1") Long id,

        @Schema(description = "카테고리 ID", example = "1") Long categoryId,

        @Schema(description = "카테고리 i18n 키 접미사", example = "korean") String categoryCode,

        @Schema(description = "음식점명. 요청 locale의 번역본이며, 번역이 없으면 원본(ko)",
                example = "서울 김치찌개")
        String name,

        @Schema(description = "음식점 설명. 번역 규칙은 name과 같다") String description,

        @Schema(description = "주소", example = "서울시 강남구 테헤란로 1") String address,

        @Schema(description = "전화번호", example = "02-1234-5678") String phoneNumber,

        @Schema(description = "배달비 (원)", example = "3000") int deliveryFee,

        @Schema(description = "최소 주문 금액 (원)", example = "12000") int minOrderAmount,

        @Schema(description = "대표 이미지 URL") String imageUrl,

        @Schema(description = "응답에 적용된 locale", example = "ko") String locale
) {

    public static RestaurantResponse of(Restaurant restaurant, String locale) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getCategory().getId(),
                restaurant.getCategory().getCode(),
                restaurant.resolveName(locale),
                restaurant.resolveDescription(locale),
                restaurant.getAddress(),
                restaurant.getPhoneNumber(),
                restaurant.getDeliveryFee(),
                restaurant.getMinOrderAmount(),
                restaurant.getImageUrl(),
                locale
        );
    }
}
