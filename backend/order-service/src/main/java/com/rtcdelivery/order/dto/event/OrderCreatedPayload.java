package com.rtcdelivery.order.dto.event;

public record OrderCreatedPayload(
        Long orderId,
        Long memberId,
        Long restaurantId,
        int totalAmount,
        String currency
) {}
