package com.rtcdelivery.payment.dto.event;

public record PaymentResultPayload(
        Long paymentId,
        Long orderId,
        Long memberId,
        int amount,
        String reason
) {}
