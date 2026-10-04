package com.rtcdelivery.order.controller;

import com.rtcdelivery.order.common.ApiResponse;
import com.rtcdelivery.order.domain.FulfillmentStatus;
import com.rtcdelivery.order.dto.request.OrderCreateRequest;
import com.rtcdelivery.order.dto.request.OrderStatusUpdateRequest;
import com.rtcdelivery.order.dto.response.OrderResponse;
import com.rtcdelivery.order.dto.response.PageResponse;
import com.rtcdelivery.order.security.Actor;
import com.rtcdelivery.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주문 API", description = "주문 생성, 조회, 이행 상태 전이, 취소 및 환불 API")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "주문 생성", description = "장바구니 품목과 배송 정보를 입력받아 주문을 생성합니다. Idempotency-Key 헤더는 필수입니다.")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Parameter(description = "클라이언트가 생성한 멱등성 키 (UUID)", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request,
            Authentication authentication
    ) {
        Actor actor = Actor.from(authentication);
        OrderService.CreateOrderResult result = orderService.createOrder(request, idempotencyKey, actor);

        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result.response()));
    }

    @Operation(summary = "내 주문 목록 조회", description = "로그인한 회원의 주문 내역을 최신순으로 조회합니다.")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<OrderResponse>> getMyOrders(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Actor actor = Actor.from(authentication);
        return ApiResponse.success(orderService.getMyOrders(actor, pageable));
    }

    @Operation(summary = "점주·운영자 주문 운영 목록 조회", description = "점주는 자신의 매장 주문만, 관리자는 전체 주문을 조회합니다.")
    @GetMapping("/managed")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<PageResponse<OrderResponse>> getManagedOrders(
            @Parameter(description = "특정 매장 ID 필터 (선택)") @RequestParam(required = false) Long restaurantId,
            @Parameter(description = "이행 상태 필터 (선택)") @RequestParam(required = false) FulfillmentStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Actor actor = Actor.from(authentication);
        return ApiResponse.success(orderService.getManagedOrders(restaurantId, status, actor, pageable));
    }

    @Operation(summary = "주문 상세 조회", description = "주문자 본인, 해당 매장 점주, 또는 관리자만 상세 정보를 조회할 수 있습니다.")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<OrderResponse> getOrderDetail(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Actor actor = Actor.from(authentication);
        return ApiResponse.success(orderService.getOrderDetail(id, actor));
    }

    @Operation(summary = "주문 이행 상태 변경", description = "점주 또는 관리자가 주문의 다음 이행 상태로 전이합니다 (PENDING->ACCEPTED->PREPARING->READY->DELIVERING->DELIVERED).")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<OrderResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            Authentication authentication
    ) {
        Actor actor = Actor.from(authentication);
        return ApiResponse.success(orderService.updateFulfillmentStatus(id, request.status(), actor));
    }

    @Operation(summary = "주문 취소", description = "고객은 PENDING 상태에서만, 점주·관리자는 배달 완료 전 언제든 취소할 수 있습니다.")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<OrderResponse> cancelOrder(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Actor actor = Actor.from(authentication);
        return ApiResponse.success(orderService.cancelOrder(id, actor));
    }

    @Operation(summary = "배달 완료 주문 환불 요청", description = "관리자(ROLE_ADMIN)만 배달 완료(DELIVERED) 및 결제 완료(PAID)된 주문에 대해 환불을 요청할 수 있습니다.")
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<OrderResponse> requestRefund(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Actor actor = Actor.from(authentication);
        return ApiResponse.success(orderService.requestRefund(id, actor));
    }
}
