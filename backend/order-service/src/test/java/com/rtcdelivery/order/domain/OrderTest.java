package com.rtcdelivery.order.domain;

import com.rtcdelivery.order.exception.BusinessException;
import com.rtcdelivery.order.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.builder()
                .id(1L)
                .memberId(10L)
                .restaurantId(100L)
                .restaurantOwnerId(20L)
                .restaurantName("테스트 음식점")
                .fulfillmentStatus(FulfillmentStatus.PENDING)
                .paymentStatus(PaymentStatus.PAID)
                .foodAmount(15000)
                .deliveryFee(3000)
                .totalAmount(18000)
                .recipientName("홍길동")
                .recipientPhone("010-1234-5678")
                .address("서울시 강남구")
                .idempotencyKey("uuid-1234")
                .build();
    }

    @Test
    @DisplayName("transitionFulfillment_정상_단계_순차_전이_성공")
    void transitionFulfillment_sequentialSuccess() {
        order.transitionFulfillment(FulfillmentStatus.ACCEPTED);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ACCEPTED);

        order.transitionFulfillment(FulfillmentStatus.PREPARING);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);

        order.transitionFulfillment(FulfillmentStatus.READY);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.READY);

        order.transitionFulfillment(FulfillmentStatus.DELIVERING);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERING);

        order.transitionFulfillment(FulfillmentStatus.DELIVERED);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
    }

    @Test
    @DisplayName("transitionFulfillment_건너뛰기_전이는_INVALID_ORDER_STATUS_TRANSITION_예외")
    void transitionFulfillment_skipStage_throwsException() {
        assertThatThrownBy(() -> order.transitionFulfillment(FulfillmentStatus.READY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("transitionFulfillment_미결제_상태에서_접수(ACCEPTED)는_ORDER_NOT_PAID_예외")
    void transitionFulfillment_unpaidAccept_throwsException() {
        Order unpaidOrder = Order.builder()
                .fulfillmentStatus(FulfillmentStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();

        assertThatThrownBy(() -> unpaidOrder.transitionFulfillment(FulfillmentStatus.ACCEPTED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_PAID);
    }

    @Test
    @DisplayName("cancelByCustomer_PENDING_상태에서_취소_성공_결제완료건은_REFUND_PENDING")
    void cancelByCustomer_pendingSuccess() {
        order.cancelByCustomer();

        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    }

    @Test
    @DisplayName("cancelByCustomer_ACCEPTED_이후는_ORDER_NOT_CANCELLABLE_예외")
    void cancelByCustomer_afterAccepted_throwsException() {
        order.transitionFulfillment(FulfillmentStatus.ACCEPTED);

        assertThatThrownBy(() -> order.cancelByCustomer())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE);
    }

    @Test
    @DisplayName("cancelByStaff_배달완료_이전_언제든_취소_가능")
    void cancelByStaff_success() {
        order.transitionFulfillment(FulfillmentStatus.ACCEPTED);
        order.transitionFulfillment(FulfillmentStatus.PREPARING);

        order.cancelByStaff();

        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    }

    @Test
    @DisplayName("cancelByStaff_배달완료(DELIVERED)는_취소_불가")
    void cancelByStaff_delivered_throwsException() {
        order.transitionFulfillment(FulfillmentStatus.ACCEPTED);
        order.transitionFulfillment(FulfillmentStatus.PREPARING);
        order.transitionFulfillment(FulfillmentStatus.READY);
        order.transitionFulfillment(FulfillmentStatus.DELIVERING);
        order.transitionFulfillment(FulfillmentStatus.DELIVERED);

        assertThatThrownBy(() -> order.cancelByStaff())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("requestRefund_DELIVERED이고_PAID일_때만_환불요청_가능")
    void requestRefund_success() {
        order.transitionFulfillment(FulfillmentStatus.ACCEPTED);
        order.transitionFulfillment(FulfillmentStatus.PREPARING);
        order.transitionFulfillment(FulfillmentStatus.READY);
        order.transitionFulfillment(FulfillmentStatus.DELIVERING);
        order.transitionFulfillment(FulfillmentStatus.DELIVERED);

        order.requestRefund();

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    }

    @Test
    @DisplayName("requestRefund_DELIVERED가_아니면_REFUND_NOT_ALLOWED")
    void requestRefund_notDelivered_throwsException() {
        assertThatThrownBy(() -> order.requestRefund())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);
    }
}
