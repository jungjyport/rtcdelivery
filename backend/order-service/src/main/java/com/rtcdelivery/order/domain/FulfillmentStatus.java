package com.rtcdelivery.order.domain;

public enum FulfillmentStatus {
    PENDING,
    ACCEPTED,
    PREPARING,
    READY,
    DELIVERING,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(FulfillmentStatus next) {
        if (next == null || next == CANCELLED) {
            return false;
        }
        return switch (this) {
            case PENDING -> next == ACCEPTED;
            case ACCEPTED -> next == PREPARING;
            case PREPARING -> next == READY;
            case READY -> next == DELIVERING;
            case DELIVERING -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
