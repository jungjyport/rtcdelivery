package com.rtcdelivery.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "결제 승인 요청 DTO")
public record PaymentApproveRequest(
        @Schema(description = "카드 번호 끝 4자리 (0000 입력 시 승인 거절 시뮬레이션)", example = "1234")
        @NotBlank(message = "카드 번호 끝 4자리는 필수입니다.")
        @Pattern(regexp = "^\\d{4}$", message = "카드 번호 끝자리는 4자리 숫자여야 합니다.")
        String cardLast4
) {}
