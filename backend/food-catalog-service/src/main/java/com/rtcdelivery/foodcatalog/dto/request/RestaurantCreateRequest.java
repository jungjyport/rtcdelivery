package com.rtcdelivery.foodcatalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 음식점 등록 요청. 텍스트는 원본 언어(ko)로 받는다. 다른 locale의 값은
 * translation-service가 비동기로 채우므로 요청에 포함하지 않는다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "음식점 등록 요청 DTO")
public class RestaurantCreateRequest {

    @NotNull(message = "카테고리는 필수입니다.")
    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @NotBlank(message = "음식점명은 필수입니다.")
    @Size(max = 255, message = "음식점명은 최대 255자입니다.")
    @Schema(description = "음식점명 (한국어 원본)", example = "서울 김치찌개")
    private String name;

    @Schema(description = "음식점 설명 (한국어 원본)", example = "30년 전통 김치찌개 전문점")
    private String description;

    @NotBlank(message = "주소는 필수입니다.")
    @Size(max = 255, message = "주소는 최대 255자입니다.")
    @Schema(description = "주소", example = "서울시 강남구 테헤란로 1")
    private String address;

    @Size(max = 30, message = "전화번호는 최대 30자입니다.")
    @Schema(description = "전화번호", example = "02-1234-5678")
    private String phoneNumber;

    @Min(value = 0, message = "배달비는 0 이상이어야 합니다.")
    @Schema(description = "배달비 (원)", example = "3000")
    private Integer deliveryFee;

    @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다.")
    @Schema(description = "최소 주문 금액 (원)", example = "12000")
    private Integer minOrderAmount;

    @Size(max = 500, message = "이미지 URL은 최대 500자입니다.")
    @Schema(description = "대표 이미지 URL")
    private String imageUrl;
}
