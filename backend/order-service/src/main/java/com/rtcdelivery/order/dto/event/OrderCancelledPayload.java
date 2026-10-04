package com.rtcdelivery.order.dto.event;

public record OrderCancelledPayload(
        Long orderId,
        Long memberId,
        String reason
) {}
