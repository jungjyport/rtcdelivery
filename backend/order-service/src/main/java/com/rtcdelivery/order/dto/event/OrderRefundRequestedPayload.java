package com.rtcdelivery.order.dto.event;

public record OrderRefundRequestedPayload(
        Long orderId,
        Long memberId
) {}
