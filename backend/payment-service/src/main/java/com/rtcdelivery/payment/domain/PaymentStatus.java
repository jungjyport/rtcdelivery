package com.rtcdelivery.payment.domain;

public enum PaymentStatus {
    AWAITING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDING,
    REFUNDED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == REFUNDED;
    }
}
