package com.rtcdelivery.payment.dto.response;

import com.rtcdelivery.payment.domain.Payment;
import com.rtcdelivery.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "결제 정보 응답 DTO")
public record PaymentResponse(
        @Schema(description = "결제 ID", example = "1")
        Long id,

        @Schema(description = "주문 ID", example = "100")
        Long orderId,

        @Schema(description = "결제 회원 ID", example = "10")
        Long memberId,

        @Schema(description = "결제 금액", example = "25000")
        int amount,

        @Schema(description = "통화 단위", example = "KRW")
        String currency,

        @Schema(description = "결제 상태", example = "COMPLETED")
        PaymentStatus status,

        @Schema(description = "결제 수단", example = "CARD")
        String method,

        @Schema(description = "PG 제공자", example = "MOCK")
        String pgProvider,

        @Schema(description = "PG 승인 번호", example = "MOCK-A1B2C3D4E5F67890")
        String pgApprovalCode,

        @Schema(description = "카드 번호 끝 4자리", example = "1234")
        String cardLast4,

        @Schema(description = "실패 사유", example = "DECLINED_BY_PG")
        String failureReason,

        @Schema(description = "결제 생성 시각")
        LocalDateTime createdAt,

        @Schema(description = "결제 수정 시각")
        LocalDateTime updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getMemberId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getPgProvider(),
                payment.getPgApprovalCode(),
                payment.getCardLast4(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
