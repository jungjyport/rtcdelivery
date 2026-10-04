package com.rtcdelivery.order.service;

import com.rtcdelivery.order.client.FoodCatalogClient;
import com.rtcdelivery.order.client.dto.OrderSnapshotResponse;
import com.rtcdelivery.order.domain.FulfillmentStatus;
import com.rtcdelivery.order.domain.Order;
import com.rtcdelivery.order.domain.OrderItem;
import com.rtcdelivery.order.domain.PaymentStatus;
import com.rtcdelivery.order.dto.event.EventEnvelope;
import com.rtcdelivery.order.dto.event.PaymentResultPayload;
import com.rtcdelivery.order.dto.request.OrderCreateRequest;
import com.rtcdelivery.order.dto.response.OrderResponse;
import com.rtcdelivery.order.dto.response.PageResponse;
import com.rtcdelivery.order.exception.BusinessException;
import com.rtcdelivery.order.exception.ErrorCode;
import com.rtcdelivery.order.repository.OrderRepository;
import com.rtcdelivery.order.security.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long MEMBER_ID = 10L;
    private static final Long OWNER_ID = 20L;
    private static final Long OTHER_MEMBER_ID = 99L;
    private static final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private FoodCatalogClient foodCatalogClient;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private OrderService orderService;

    private OrderSnapshotResponse snapshot;
    private Order order;

    @BeforeEach
    void setUp() {
        snapshot = new OrderSnapshotResponse(
                1L,
                OWNER_ID,
                true,
                "서울 김치찌개",
                3000,
                12000,
                List.of(
                        new OrderSnapshotResponse.FoodSnapshotResponse(100L, "김치찌개", 9000, false),
                        new OrderSnapshotResponse.FoodSnapshotResponse(101L, "계란말이", 5000, false),
                        new OrderSnapshotResponse.FoodSnapshotResponse(102L, "품절메뉴", 8000, true)
                )
        );

        order = Order.builder()
                .id(1L)
                .memberId(MEMBER_ID)
                .restaurantId(1L)
                .restaurantOwnerId(OWNER_ID)
                .restaurantName("서울 김치찌개")
                .fulfillmentStatus(FulfillmentStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .foodAmount(18000)
                .deliveryFee(3000)
                .totalAmount(21000)
                .recipientName("홍길동")
                .recipientPhone("010-1234-5678")
                .address("서울시 강남구")
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();

        order.addItem(OrderItem.builder()
                .foodId(100L)
                .foodName("김치찌개")
                .unitPrice(9000)
                .quantity(2)
                .lineAmount(18000)
                .build());
    }

    @Test
    @DisplayName("createOrder_신규_주문_생성_성공_카탈로그_스냅샷_기반으로_금액_계산_및_아웃박스_발행")
    void createOrder_success() {
        OrderCreateRequest request = new OrderCreateRequest(
                1L,
                List.of(
                        new OrderCreateRequest.OrderItemRequest(100L, 2),
                        new OrderCreateRequest.OrderItemRequest(101L, 1)
                ),
                "홍길동",
                "010-1234-5678",
                "서울시 강남구",
                "문 앞에 두세요"
        );

        given(orderRepository.findByMemberIdAndIdempotencyKey(MEMBER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
        given(foodCatalogClient.getOrderSnapshot(1L)).willReturn(snapshot);
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            return Order.builder()
                    .id(100L)
                    .memberId(o.getMemberId())
                    .restaurantId(o.getRestaurantId())
                    .restaurantOwnerId(o.getRestaurantOwnerId())
                    .restaurantName(o.getRestaurantName())
                    .fulfillmentStatus(o.getFulfillmentStatus())
                    .paymentStatus(o.getPaymentStatus())
                    .foodAmount(o.getFoodAmount())
                    .deliveryFee(o.getDeliveryFee())
                    .totalAmount(o.getTotalAmount())
                    .recipientName(o.getRecipientName())
                    .recipientPhone(o.getRecipientPhone())
                    .address(o.getAddress())
                    .idempotencyKey(o.getIdempotencyKey())
                    .build();
        });

        Actor actor = new Actor(MEMBER_ID, false, "ROLE_USER");
        OrderService.CreateOrderResult result = orderService.createOrder(request, IDEMPOTENCY_KEY, actor);

        assertThat(result.isCreated()).isTrue();
        assertThat(result.response().id()).isEqualTo(100L);
        assertThat(result.response().foodAmount()).isEqualTo(23000); // 9000*2 + 5000*1
        assertThat(result.response().deliveryFee()).isEqualTo(3000);
        assertThat(result.response().totalAmount()).isEqualTo(26000);

        verify(outboxService).recordOrderCreated(any(Order.class));
    }

    @Test
    @DisplayName("createOrder_동일_회원의_동일_멱등키_재요청은_기존_주문_반환하고_카탈로그를_재호출하지_않음")
    void createOrder_idempotentResend_returnsExisting() {
        given(orderRepository.findByMemberIdAndIdempotencyKey(MEMBER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.of(order));

        OrderCreateRequest request = new OrderCreateRequest(
                1L, List.of(new OrderCreateRequest.OrderItemRequest(100L, 2)),
                "홍길동", "010-1234-5678", "서울시 강남구", null
        );

        Actor actor = new Actor(MEMBER_ID, false, "ROLE_USER");
        OrderService.CreateOrderResult result = orderService.createOrder(request, IDEMPOTENCY_KEY, actor);

        assertThat(result.isCreated()).isFalse();
        assertThat(result.response().id()).isEqualTo(1L);

        verify(foodCatalogClient, never()).getOrderSnapshot(any());
        verify(outboxService, never()).recordOrderCreated(any());
    }

    @Test
    @DisplayName("createOrder_멱등키가_UUID_형식이_아니면_VALIDATION_ERROR")
    void createOrder_invalidIdempotencyKey_throwsException() {
        OrderCreateRequest request = new OrderCreateRequest(
                1L, List.of(new OrderCreateRequest.OrderItemRequest(100L, 2)),
                "홍길동", "010-1234-5678", "서울시 강남구", null
        );
        Actor actor = new Actor(MEMBER_ID, false, "ROLE_USER");

        assertThatThrownBy(() -> orderService.createOrder(request, "invalid-uuid", actor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("createOrder_품절_메뉴_포함_시_FOOD_UNAVAILABLE")
    void createOrder_soldOutFood_throwsException() {
        OrderCreateRequest request = new OrderCreateRequest(
                1L, List.of(new OrderCreateRequest.OrderItemRequest(102L, 1)),
                "홍길동", "010-1234-5678", "서울시 강남구", null
        );
        given(orderRepository.findByMemberIdAndIdempotencyKey(MEMBER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
        given(foodCatalogClient.getOrderSnapshot(1L)).willReturn(snapshot);

        Actor actor = new Actor(MEMBER_ID, false, "ROLE_USER");
        assertThatThrownBy(() -> orderService.createOrder(request, IDEMPOTENCY_KEY, actor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FOOD_UNAVAILABLE);
    }

    @Test
    @DisplayName("createOrder_비활성_매장이면_RESTAURANT_CLOSED")
    void createOrder_closedRestaurant_throwsException() {
        OrderSnapshotResponse closedSnapshot = new OrderSnapshotResponse(
                1L, OWNER_ID, false, "영업종료식당", 3000, 10000, List.of()
        );
        given(orderRepository.findByMemberIdAndIdempotencyKey(MEMBER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
        given(foodCatalogClient.getOrderSnapshot(1L)).willReturn(closedSnapshot);

        OrderCreateRequest request = new OrderCreateRequest(
                1L, List.of(new OrderCreateRequest.OrderItemRequest(100L, 1)),
                "홍길동", "010-1234-5678", "서울시 강남구", null
        );
        Actor actor = new Actor(MEMBER_ID, false, "ROLE_USER");

        assertThatThrownBy(() -> orderService.createOrder(request, IDEMPOTENCY_KEY, actor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESTAURANT_CLOSED);
    }

    @Test
    @DisplayName("createOrder_최소주문금액_미달_시_MIN_ORDER_AMOUNT_NOT_MET")
    void createOrder_minOrderAmountNotMet_throwsException() {
        OrderCreateRequest request = new OrderCreateRequest(
                1L, List.of(new OrderCreateRequest.OrderItemRequest(101L, 1)), // 5000원 < 12000원
                "홍길동", "010-1234-5678", "서울시 강남구", null
        );
        given(orderRepository.findByMemberIdAndIdempotencyKey(MEMBER_ID, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());
        given(foodCatalogClient.getOrderSnapshot(1L)).willReturn(snapshot);

        Actor actor = new Actor(MEMBER_ID, false, "ROLE_USER");
        assertThatThrownBy(() -> orderService.createOrder(request, IDEMPOTENCY_KEY, actor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MIN_ORDER_AMOUNT_NOT_MET);
    }

    @Test
    @DisplayName("getOrderDetail_주문자_본인은_조회_성공")
    void getOrderDetail_customerSuccess() {
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        Actor actor = new Actor(MEMBER_ID, false, "ROLE_USER");
        OrderResponse response = orderService.getOrderDetail(1L, actor);

        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getOrderDetail_타인의_주문_조회_시_404_ORDER_NOT_FOUND")
    void getOrderDetail_otherUser_throwsNotFound() {
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        Actor actor = new Actor(OTHER_MEMBER_ID, false, "ROLE_USER");
        assertThatThrownBy(() -> orderService.getOrderDetail(1L, actor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("updateFulfillmentStatus_해당_가게_점주_상태_변경_성공")
    void updateFulfillmentStatus_byOwner_success() {
        order.markPaid();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        Actor ownerActor = new Actor(OWNER_ID, false, "ROLE_OWNER");
        OrderResponse response = orderService.updateFulfillmentStatus(1L, FulfillmentStatus.ACCEPTED, ownerActor);

        assertThat(response.fulfillmentStatus()).isEqualTo(FulfillmentStatus.ACCEPTED);
    }

    @Test
    @DisplayName("updateFulfillmentStatus_다른_점주가_호출하면_ORDER_NOT_FOUND")
    void updateFulfillmentStatus_byOtherOwner_throwsNotFound() {
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        Actor otherOwnerActor = new Actor(999L, false, "ROLE_OWNER");
        assertThatThrownBy(() -> orderService.updateFulfillmentStatus(1L, FulfillmentStatus.ACCEPTED, otherOwnerActor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("cancelOrder_고객은_PENDING_상태에서_취소_성공_ORDER_CANCELLED_아웃박스_발행")
    void cancelOrder_byCustomer_success() {
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        Actor actor = new Actor(MEMBER_ID, false, "ROLE_USER");
        OrderResponse response = orderService.cancelOrder(1L, actor);

        assertThat(response.fulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
        verify(outboxService).recordOrderCancelled(order, "CUSTOMER");
    }

    @Test
    @DisplayName("requestRefund_관리자_환불요청_성공_ORDER_REFUND_REQUESTED_아웃박스_발행")
    void requestRefund_byAdmin_success() {
        order.markPaid();
        order.transitionFulfillment(FulfillmentStatus.ACCEPTED);
        order.transitionFulfillment(FulfillmentStatus.PREPARING);
        order.transitionFulfillment(FulfillmentStatus.READY);
        order.transitionFulfillment(FulfillmentStatus.DELIVERING);
        order.transitionFulfillment(FulfillmentStatus.DELIVERED);

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        Actor adminActor = new Actor(1L, true, "ROLE_ADMIN");
        OrderResponse response = orderService.requestRefund(1L, adminActor);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        verify(outboxService).recordOrderRefundRequested(order);
    }

    @Test
    @DisplayName("processPaymentEvent_PAYMENT_COMPLETED_도착_시_주문_PAID로_전이")
    void processPaymentEvent_paymentCompleted_marksPaid() {
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        EventEnvelope<PaymentResultPayload> envelope = EventEnvelope.of(
                "evt-1", "PAYMENT_COMPLETED", "PAYMENT", "10",
                new PaymentResultPayload(10L, 1L, MEMBER_ID, 21000, null)
        );

        orderService.processPaymentEvent(envelope);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("processPaymentEvent_취소된_주문에_늦게_도착한_PAYMENT_COMPLETED는_환불요청_발행")
    void processPaymentEvent_cancelledOrderCompleted_triggersRefund() {
        order.cancelByCustomer();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        EventEnvelope<PaymentResultPayload> envelope = EventEnvelope.of(
                "evt-2", "PAYMENT_COMPLETED", "PAYMENT", "10",
                new PaymentResultPayload(10L, 1L, MEMBER_ID, 21000, null)
        );

        orderService.processPaymentEvent(envelope);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        verify(outboxService).recordOrderRefundRequested(order);
    }
}
