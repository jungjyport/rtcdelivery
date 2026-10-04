package com.rtcdelivery.payment.service;

import com.rtcdelivery.payment.client.MockPaymentGateway;
import com.rtcdelivery.payment.domain.Payment;
import com.rtcdelivery.payment.domain.PaymentStatus;
import com.rtcdelivery.payment.domain.Role;
import com.rtcdelivery.payment.dto.event.OrderCancelledPayload;
import com.rtcdelivery.payment.dto.event.OrderCreatedPayload;
import com.rtcdelivery.payment.dto.event.OrderRefundRequestedPayload;
import com.rtcdelivery.payment.dto.request.PaymentApproveRequest;
import com.rtcdelivery.payment.dto.response.PaymentResponse;
import com.rtcdelivery.payment.exception.BusinessException;
import com.rtcdelivery.payment.exception.ErrorCode;
import com.rtcdelivery.payment.repository.PaymentRepository;
import com.rtcdelivery.payment.security.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MockPaymentGateway paymentGateway;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("getPaymentByOrderId_주문자 본인은 200 OK")
    void getPaymentByOrderId_successForOwner() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.AWAITING)
                .build();

        given(paymentRepository.findByOrderId(10L)).willReturn(Optional.of(payment));

        Actor userActor = new Actor(5L, Role.ROLE_USER);
        PaymentResponse response = paymentService.getPaymentByOrderId(10L, userActor);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.amount()).isEqualTo(20000);
    }

    @Test
    @DisplayName("getPaymentByOrderId_다른 회원은 PAYMENT_NOT_FOUND 예외 발생")
    void getPaymentByOrderId_otherUser_notFound() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.AWAITING)
                .build();

        given(paymentRepository.findByOrderId(10L)).willReturn(Optional.of(payment));

        Actor otherUser = new Actor(99L, Role.ROLE_USER);
        assertThatThrownBy(() -> paymentService.getPaymentByOrderId(10L, otherUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("approvePayment_정상 승인 처리 시 COMPLETED 상태 및 Outbox 이벤트 발행")
    void approvePayment_success() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.AWAITING)
                .build();

        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(paymentGateway.approve(eq(10L), eq(20000), eq("1234")))
                .willReturn(MockPaymentGateway.ApproveResult.approved("MOCK-ABC123456"));

        Actor actor = new Actor(5L, Role.ROLE_USER);
        PaymentApproveRequest req = new PaymentApproveRequest("1234");

        PaymentResponse response = paymentService.approvePayment(1L, req, actor);

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.pgApprovalCode()).isEqualTo("MOCK-ABC123456");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        verify(outboxService).recordEvent(eq("Payment"), eq("1"), eq("PAYMENT_COMPLETED"), anyString(), eq("10"), any());
    }

    @Test
    @DisplayName("approvePayment_카드 거절 시 FAILED 상태 및 PAYMENT_FAILED 예외 발생")
    void approvePayment_declined() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.AWAITING)
                .build();

        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(paymentGateway.approve(eq(10L), eq(20000), eq("0000")))
                .willReturn(MockPaymentGateway.ApproveResult.declined("DECLINED_BY_PG"));

        Actor actor = new Actor(5L, Role.ROLE_USER);
        PaymentApproveRequest req = new PaymentApproveRequest("0000");

        assertThatThrownBy(() -> paymentService.approvePayment(1L, req, actor))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(outboxService).recordEvent(eq("Payment"), eq("1"), eq("PAYMENT_FAILED"), anyString(), eq("10"), any());
    }

    @Test
    @DisplayName("approvePayment_이미 COMPLETED인 경우 멱등 성공 반환")
    void approvePayment_alreadyCompleted() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.COMPLETED)
                .pgApprovalCode("MOCK-EXISTING")
                .cardLast4("1234")
                .build();

        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        Actor actor = new Actor(5L, Role.ROLE_USER);
        PaymentApproveRequest req = new PaymentApproveRequest("1234");

        PaymentResponse response = paymentService.approvePayment(1L, req, actor);

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentGateway, never()).approve(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("approvePayment_AWAITING이 아니면 PAYMENT_NOT_APPROVABLE 예외")
    void approvePayment_notApprovable() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.CANCELLED)
                .build();

        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        Actor actor = new Actor(5L, Role.ROLE_USER);
        PaymentApproveRequest req = new PaymentApproveRequest("1234");

        assertThatThrownBy(() -> paymentService.approvePayment(1L, req, actor))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_APPROVABLE));
    }

    @Test
    @DisplayName("handleOrderCreated_신규 주문 이벤트 수신 시 AWAITING 상태로 Payment 생성")
    void handleOrderCreated_new() {
        OrderCreatedPayload payload = new OrderCreatedPayload(10L, 5L, 1L, 25000);
        given(paymentRepository.existsByOrderId(10L)).willReturn(false);

        paymentService.handleOrderCreated(payload);

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("handleOrderCreated_중복 수신 시 결제 생성 생략")
    void handleOrderCreated_duplicate() {
        OrderCreatedPayload payload = new OrderCreatedPayload(10L, 5L, 1L, 25000);
        given(paymentRepository.existsByOrderId(10L)).willReturn(true);

        paymentService.handleOrderCreated(payload);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleOrderCancelled_AWAITING 상태인 경우 CANCELLED 및 PAYMENT_FAILED 이벤트 발행")
    void handleOrderCancelled_awaiting() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.AWAITING)
                .build();

        given(paymentRepository.findByOrderId(10L)).willReturn(Optional.of(payment));

        OrderCancelledPayload payload = new OrderCancelledPayload(10L, 5L, "USER_CANCELLED");
        paymentService.handleOrderCancelled(payload);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(outboxService).recordEvent(eq("Payment"), eq("1"), eq("PAYMENT_FAILED"), anyString(), eq("10"), any());
    }

    @Test
    @DisplayName("handleOrderCancelled_COMPLETED 상태인 경우 PG 환불 및 REFUNDED 상태 전이")
    void handleOrderCancelled_completed() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.COMPLETED)
                .pgApprovalCode("MOCK-ABC")
                .build();

        given(paymentRepository.findByOrderId(10L)).willReturn(Optional.of(payment));
        given(paymentGateway.refund(eq(10L), eq("MOCK-ABC"), eq(20000)))
                .willReturn(MockPaymentGateway.RefundResult.ok());

        OrderCancelledPayload payload = new OrderCancelledPayload(10L, 5L, "USER_CANCELLED");
        paymentService.handleOrderCancelled(payload);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(outboxService).recordEvent(eq("Payment"), eq("1"), eq("PAYMENT_REFUNDED"), anyString(), eq("10"), any());
    }

    @Test
    @DisplayName("handleOrderRefundRequested_COMPLETED 상태인 경우 PG 환불 및 REFUNDED 상태 전이")
    void handleOrderRefundRequested_completed() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.COMPLETED)
                .pgApprovalCode("MOCK-ABC")
                .build();

        given(paymentRepository.findByOrderId(10L)).willReturn(Optional.of(payment));
        given(paymentGateway.refund(eq(10L), eq("MOCK-ABC"), eq(20000)))
                .willReturn(MockPaymentGateway.RefundResult.ok());

        OrderRefundRequestedPayload payload = new OrderRefundRequestedPayload(10L, 5L, 20000, "ADMIN_REFUND");
        paymentService.handleOrderRefundRequested(payload);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(outboxService).recordEvent(eq("Payment"), eq("1"), eq("PAYMENT_REFUNDED"), anyString(), eq("10"), any());
    }
}
