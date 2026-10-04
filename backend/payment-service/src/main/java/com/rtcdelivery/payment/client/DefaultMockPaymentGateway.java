package com.rtcdelivery.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class DefaultMockPaymentGateway implements MockPaymentGateway {

    @Override
    public ApproveResult approve(Long orderId, int amount, String cardLast4) {
        log.info("MockPaymentGateway approving payment: orderId={}, amount={}, cardLast4={}",
                orderId, amount, cardLast4);

        if (cardLast4 != null && cardLast4.endsWith("0000")) {
            log.warn("MockPaymentGateway declined payment (card ending in 0000): orderId={}", orderId);
            return ApproveResult.declined("DECLINED_BY_PG");
        }

        String approvalCode = "MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("MockPaymentGateway approved: orderId={}, approvalCode={}", orderId, approvalCode);
        return ApproveResult.approved(approvalCode);
    }

    @Override
    public RefundResult refund(Long orderId, String pgApprovalCode, int amount) {
        log.info("MockPaymentGateway refunding payment: orderId={}, approvalCode={}, amount={}",
                orderId, pgApprovalCode, amount);
        return RefundResult.ok();
    }
}
