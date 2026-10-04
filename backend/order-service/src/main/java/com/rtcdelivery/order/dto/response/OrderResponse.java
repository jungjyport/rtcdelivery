package com.rtcdelivery.order.dto.response;

import com.rtcdelivery.order.domain.FulfillmentStatus;
import com.rtcdelivery.order.domain.Order;
import com.rtcdelivery.order.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "주문 응답 DTO")
public record OrderResponse(
        @Schema(description = "주문 ID", example = "100")
        Long id,

        @Schema(description = "음식점 ID", example = "1")
        Long restaurantId,

        @Schema(description = "주문 시점 매장 원본명(ko)", example = "서울 김치찌개")
        String restaurantName,

        @Schema(description = "이행 상태", example = "PENDING")
        FulfillmentStatus fulfillmentStatus,

        @Schema(description = "결제 상태", example = "UNPAID")
        PaymentStatus paymentStatus,

        @Schema(description = "음식 합계 금액", example = "18000")
        int foodAmount,

        @Schema(description = "배달비", example = "3000")
        int deliveryFee,

        @Schema(description = "총 결제 금액", example = "21000")
        int totalAmount,

        @Schema(description = "수령인 이름", example = "홍길동")
        String recipientName,

        @Schema(description = "수령인 연락처", example = "010-1234-5678")
        String recipientPhone,

        @Schema(description = "배송지 주소", example = "서울시 강남구 테헤란로 1")
        String address,

        @Schema(description = "요청사항", example = "문 앞에 두세요")
        String requestNote,

        @Schema(description = "주문 품목 목록")
        List<OrderItemResponse> items,

        @Schema(description = "주문 생성 일시")
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getRestaurantId(),
                order.getRestaurantName(),
                order.getFulfillmentStatus(),
                order.getPaymentStatus(),
                order.getFoodAmount(),
                order.getDeliveryFee(),
                order.getTotalAmount(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getAddress(),
                order.getRequestNote(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt()
        );
    }
}
