package com.rtcdelivery.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "주문 생성 요청 DTO")
public record OrderCreateRequest(
        @Schema(description = "음식점 ID", example = "1")
        @NotNull(message = "음식점 ID는 필수입니다.")
        Long restaurantId,

        @Schema(description = "주문 품목 목록")
        @NotEmpty(message = "주문 품목은 1개 이상이어야 합니다.")
        List<@Valid OrderItemRequest> items,

        @Schema(description = "수령인 이름", example = "홍길동")
        @NotBlank(message = "수령인 이름은 필수입니다.")
        @Size(max = 50, message = "수령인 이름은 최대 50자입니다.")
        String recipientName,

        @Schema(description = "수령인 연락처", example = "010-1234-5678")
        @NotBlank(message = "수령인 연락처는 필수입니다.")
        @Size(max = 30, message = "수령인 연락처는 최대 30자입니다.")
        String recipientPhone,

        @Schema(description = "배송지 주소", example = "서울시 강남구 테헤란로 1")
        @NotBlank(message = "배송지 주소는 필수입니다.")
        @Size(max = 255, message = "배송지 주소는 최대 255자입니다.")
        String address,

        @Schema(description = "요청사항", example = "문 앞에 두고 벨 눌러주세요.")
        @Size(max = 200, message = "요청사항은 최대 200자입니다.")
        String requestNote
) {

    @Schema(description = "주문 품목 항목")
    public record OrderItemRequest(
            @Schema(description = "메뉴 ID", example = "10")
            @NotNull(message = "메뉴 ID는 필수입니다.")
            Long foodId,

            @Schema(description = "주문 수량", example = "2")
            @NotNull(message = "수량은 필수입니다.")
            @Min(value = 1, message = "수량은 최소 1개 이상이어야 합니다.")
            @Max(value = 99, message = "수량은 최대 99개까지 가능합니다.")
            Integer quantity
    ) {}
}
