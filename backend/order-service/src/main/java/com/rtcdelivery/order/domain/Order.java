package com.rtcdelivery.order.domain;

import com.rtcdelivery.order.exception.BusinessException;
import com.rtcdelivery.order.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_member_idempotency", columnNames = {"member_id", "idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_orders_member", columnList = "member_id"),
                @Index(name = "idx_orders_restaurant", columnList = "restaurant_id"),
                @Index(name = "idx_orders_restaurant_owner", columnList = "restaurant_owner_id")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "restaurant_owner_id", nullable = false)
    private Long restaurantOwnerId;

    @Column(name = "restaurant_name", nullable = false, length = 255)
    private String restaurantName;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 30)
    private FulfillmentStatus fulfillmentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Column(name = "food_amount", nullable = false)
    private int foodAmount;

    @Column(name = "delivery_fee", nullable = false)
    private int deliveryFee;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 30)
    private String recipientPhone;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "request_note", length = 200)
    private String requestNote;

    @Column(name = "idempotency_key", nullable = false, length = 36)
    private String idempotencyKey;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        this.items.add(item);
        item.setOrder(this);
    }

    /**
     * 다음 이행 상태로 전이한다 (점주 또는 관리자).
     */
    public void transitionFulfillment(FulfillmentStatus next) {
        if (!this.fulfillmentStatus.canTransitionTo(next)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        if (this.fulfillmentStatus == FulfillmentStatus.PENDING && next == FulfillmentStatus.ACCEPTED) {
            if (this.paymentStatus != PaymentStatus.PAID) {
                throw new BusinessException(ErrorCode.ORDER_NOT_PAID);
            }
        }
        this.fulfillmentStatus = next;
    }

    /**
     * 고객에 의한 취소 (PENDING 상태만 가능).
     */
    public void cancelByCustomer() {
        if (this.fulfillmentStatus == FulfillmentStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (this.fulfillmentStatus != FulfillmentStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        applyCancel();
    }

    /**
     * 점주 또는 관리자에 의한 취소 (종료 전 상태 가능).
     */
    public void cancelByStaff() {
        if (this.fulfillmentStatus == FulfillmentStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (this.fulfillmentStatus == FulfillmentStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        applyCancel();
    }

    private void applyCancel() {
        this.fulfillmentStatus = FulfillmentStatus.CANCELLED;
        if (this.paymentStatus == PaymentStatus.PAID) {
            this.paymentStatus = PaymentStatus.REFUND_PENDING;
        }
    }

    /**
     * 관리자에 의한 배달 완료 주문 환불 요청.
     */
    public void requestRefund() {
        if (this.fulfillmentStatus != FulfillmentStatus.DELIVERED || this.paymentStatus != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }
        this.paymentStatus = PaymentStatus.REFUND_PENDING;
    }

    public void markRefundPending() {
        this.paymentStatus = PaymentStatus.REFUND_PENDING;
    }

    public void markPaid() {
        this.paymentStatus = PaymentStatus.PAID;
    }

    public void markPaymentFailed() {
        if (this.paymentStatus == PaymentStatus.UNPAID) {
            this.paymentStatus = PaymentStatus.PAYMENT_FAILED;
        }
    }

    public void markRefunded() {
        this.paymentStatus = PaymentStatus.REFUNDED;
    }

    public void restorePaidAfterRefundFailure() {
        if (this.paymentStatus == PaymentStatus.REFUND_PENDING) {
            this.paymentStatus = PaymentStatus.PAID;
        }
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId != null && this.memberId.equals(memberId);
    }

    public boolean isManagedBy(Long ownerId) {
        return this.restaurantOwnerId != null && this.restaurantOwnerId.equals(ownerId);
    }
}
