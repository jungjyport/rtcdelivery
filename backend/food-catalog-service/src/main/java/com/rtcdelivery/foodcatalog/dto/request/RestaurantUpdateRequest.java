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
 * <p>{@code name} 또는 {@code description}이 바뀌면 기존 번역은 옛 원문을 가리키게 되므로
 * 서비스 계층에서 폐기하고 재번역을 요청한다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "음식점 수정 요청 DTO")
public class RestaurantUpdateRequest {

    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @Size(max = 255, message = "음식점명은 최대 255자입니다.")
    @Schema(description = "음식점명 (한국어 원본)", example = "서울 김치찌개")
    private String name;

    @Schema(description = "음식점 설명 (한국어 원본)")
    private String description;

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
