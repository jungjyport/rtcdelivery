package com.rtcdelivery.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    @Test
    @DisplayName("markCompleted 호출 시 COMPLETED 상태와 승인번호, 카드번호가 세팅된다")
    void markCompleted() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.AWAITING)
                .build();

        payment.markCompleted("MOCK-APPROVAL-1234", "1234");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPgApprovalCode()).isEqualTo("MOCK-APPROVAL-1234");
        assertThat(payment.getCardLast4()).isEqualTo("1234");
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("markFailed 호출 시 FAILED 상태와 실패사유가 세팅된다")
    void markFailed() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.AWAITING)
                .build();

        payment.markFailed("DECLINED_BY_PG");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("DECLINED_BY_PG");
    }

    @Test
    @DisplayName("markCancelled 호출 시 CANCELLED 상태와 사유가 세팅된다")
    void markCancelled() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.AWAITING)
                .build();

        payment.markCancelled("USER_CANCELLED");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getFailureReason()).isEqualTo("USER_CANCELLED");
    }

    @Test
    @DisplayName("markRefunding 및 markRefunded 호출 시 REFUNDED 상태로 전이된다")
    void markRefunded() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .memberId(5L)
                .amount(20000)
                .status(PaymentStatus.COMPLETED)
                .build();

        payment.markRefunding();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDING);

        payment.markRefunded("ADMIN_REFUND");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getFailureReason()).isEqualTo("ADMIN_REFUND");
    }
}
