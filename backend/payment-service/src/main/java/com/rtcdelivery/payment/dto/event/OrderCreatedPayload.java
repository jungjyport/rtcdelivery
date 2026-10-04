package com.rtcdelivery.payment.dto.event;

public record OrderCreatedPayload(
        Long orderId,
        Long memberId,
        Long restaurantId,
        int totalAmount
) {}
