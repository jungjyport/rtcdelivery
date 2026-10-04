package com.rtcdelivery.payment.dto.event;

public record OrderCancelledPayload(
        Long orderId,
        Long memberId,
        String reason
) {}
