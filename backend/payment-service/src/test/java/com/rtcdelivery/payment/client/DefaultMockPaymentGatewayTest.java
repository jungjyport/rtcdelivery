package com.rtcdelivery.payment.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMockPaymentGatewayTest {

    private final DefaultMockPaymentGateway gateway = new DefaultMockPaymentGateway();

    @Test
    @DisplayName("0000으로 끝나는 카드는 승인 거절된다")
    void approve_declinedFor0000() {
        MockPaymentGateway.ApproveResult result = gateway.approve(10L, 20000, "0000");

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo("DECLINED_BY_PG");
        assertThat(result.approvalCode()).isNull();
    }

    @Test
    @DisplayName("일반 카드번호는 승인되고 MOCK- 형식의 승인코드가 발급된다")
    void approve_success() {
        MockPaymentGateway.ApproveResult result = gateway.approve(10L, 20000, "1234");

        assertThat(result.success()).isTrue();
        assertThat(result.approvalCode()).startsWith("MOCK-");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("환불 요청은 성공한다")
    void refund_success() {
        MockPaymentGateway.RefundResult result = gateway.refund(10L, "MOCK-1234", 20000);

        assertThat(result.success()).isTrue();
        assertThat(result.failureReason()).isNull();
    }
}
