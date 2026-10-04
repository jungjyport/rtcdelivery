package com.rtcdelivery.foodcatalog.controller;

import com.rtcdelivery.foodcatalog.common.ApiResponse;
import com.rtcdelivery.foodcatalog.dto.response.OrderSnapshotResponse;
import com.rtcdelivery.foodcatalog.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 연동 API", description = "타 마이크로서비스(order-service 등) 전용 내부 API")
@RestController
@RequestMapping("/internal/restaurants")
@RequiredArgsConstructor
public class InternalRestaurantController {

    private final RestaurantService restaurantService;

    @Operation(summary = "주문 시점 카탈로그 스냅샷 조회", description = "order-service 주문 생성 시 가격, 품절 여부, 최소 주문 금액, 점주 ID를 조회한다.")
    @GetMapping("/{restaurantId}/order-snapshot")
    public ApiResponse<OrderSnapshotResponse> getOrderSnapshot(@PathVariable Long restaurantId) {
        OrderSnapshotResponse snapshot = restaurantService.getOrderSnapshot(restaurantId);
        return ApiResponse.success(snapshot);
    }
}
