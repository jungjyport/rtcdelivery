package com.rtcdelivery.order.dto.request;

import com.rtcdelivery.order.domain.FulfillmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "주문 이행 상태 변경 요청 DTO")
public record OrderStatusUpdateRequest(
        @Schema(description = "변경할 다음 이행 상태", example = "ACCEPTED")
        @NotNull(message = "변경할 상태는 필수입니다.")
        FulfillmentStatus status
) {}
