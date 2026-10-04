package com.rtcdelivery.payment.dto.event;

public record OrderRefundRequestedPayload(
        Long orderId,
        Long memberId,
        int refundAmount,
        String reason
) {}
