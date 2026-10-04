package com.rtcdelivery.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "payment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_order_id", columnNames = {"order_id"})
        },
        indexes = {
                @Index(name = "idx_payment_member_id", columnList = "member_id")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int amount;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "KRW";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String method = "CARD";

    @Builder.Default
    @Column(name = "pg_provider", nullable = false, length = 20)
    private String pgProvider = "MOCK";

    @Column(name = "pg_approval_code", length = 50)
    private String pgApprovalCode;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    public void markCompleted(String approvalCode, String cardLast4) {
        this.status = PaymentStatus.COMPLETED;
        this.pgApprovalCode = approvalCode;
        this.cardLast4 = cardLast4;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = abbreviate(reason);
    }

    public void markCancelled(String reason) {
        this.status = PaymentStatus.CANCELLED;
        this.failureReason = abbreviate(reason);
    }

    public void markRefunding() {
        this.status = PaymentStatus.REFUNDING;
    }

    public void markRefunded(String reason) {
        this.status = PaymentStatus.REFUNDED;
        this.failureReason = abbreviate(reason);
    }

    private static String abbreviate(String str) {
        if (str == null) return null;
        return str.length() > 255 ? str.substring(0, 255) : str;
    }
}
