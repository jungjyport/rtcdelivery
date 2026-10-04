package com.rtcdelivery.order.dto.response;

import com.rtcdelivery.order.domain.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주문 품목 항목 응답 DTO")
public record OrderItemResponse(
        @Schema(description = "메뉴 ID", example = "10")
        Long foodId,

        @Schema(description = "주문 시점 메뉴 원본명(ko)", example = "김치찌개")
        String foodName,

        @Schema(description = "단가", example = "9000")
        int unitPrice,

        @Schema(description = "수량", example = "2")
        int quantity,

        @Schema(description = "품목 합계 금액", example = "18000")
        int lineAmount
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getFoodId(),
                item.getFoodName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineAmount()
        );
    }
}
