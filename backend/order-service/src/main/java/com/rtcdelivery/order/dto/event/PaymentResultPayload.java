package com.rtcdelivery.order.dto.event;

public record PaymentResultPayload(
        Long paymentId,
        Long orderId,
        Long memberId,
        int amount,
        String reason
) {}
