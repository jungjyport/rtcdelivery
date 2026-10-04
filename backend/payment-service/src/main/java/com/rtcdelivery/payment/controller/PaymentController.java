package com.rtcdelivery.payment.controller;

import com.rtcdelivery.payment.common.ApiResponse;
import com.rtcdelivery.payment.dto.request.PaymentApproveRequest;
import com.rtcdelivery.payment.dto.response.PaymentResponse;
import com.rtcdelivery.payment.security.Actor;
import com.rtcdelivery.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제 API", description = "결제 내역 조회 및 결제 승인 API")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "주문별 결제 조회", description = "주문 ID(orderId)로 결제 정보를 조회합니다. 주문자 본인, 점주, 또는 관리자만 조회 가능합니다.")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PaymentResponse> getPaymentByOrderId(
            @Parameter(description = "주문 ID", required = true) @RequestParam Long orderId,
            Authentication authentication
    ) {
        Actor actor = Actor.from(authentication);
        return ApiResponse.success(paymentService.getPaymentByOrderId(orderId, actor));
    }

    @Operation(summary = "결제 승인", description = "대기(AWAITING) 중인 결제를 승인 처리합니다. 카드 끝자리가 0000인 경우 승인이 거절됩니다.")
    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PaymentResponse> approvePayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentApproveRequest request,
            Authentication authentication
    ) {
        Actor actor = Actor.from(authentication);
        return ApiResponse.success(paymentService.approvePayment(id, request, actor));
    }
}
