package com.rtcdelivery.payment.client;

public interface MockPaymentGateway {

    record ApproveResult(boolean success, String approvalCode, String failureReason) {
        public static ApproveResult approved(String approvalCode) {
            return new ApproveResult(true, approvalCode, null);
        }

        public static ApproveResult declined(String failureReason) {
            return new ApproveResult(false, null, failureReason);
        }
    }

    record RefundResult(boolean success, String failureReason) {
        public static RefundResult ok() {
            return new RefundResult(true, null);
        }

        public static RefundResult fail(String failureReason) {
            return new RefundResult(false, failureReason);
        }
    }

    ApproveResult approve(Long orderId, int amount, String cardLast4);

    RefundResult refund(Long orderId, String pgApprovalCode, int amount);
}
