package com.rtcdelivery.order.client.dto;

import java.util.List;

public record OrderSnapshotResponse(
        Long restaurantId,
        Long ownerId,
        boolean active,
        String name,
        int deliveryFee,
        int minOrderAmount,
        List<FoodSnapshotResponse> foods
) {
    public record FoodSnapshotResponse(
            Long foodId,
            String name,
            int price,
            boolean soldOut
    ) {}
}
