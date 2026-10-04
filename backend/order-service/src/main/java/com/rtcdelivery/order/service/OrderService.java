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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final FoodCatalogClient foodCatalogClient;
    private final OutboxService outboxService;

    public record CreateOrderResult(OrderResponse response, boolean isCreated) {}

    @Transactional
    public CreateOrderResult createOrder(OrderCreateRequest request, String idempotencyKey, Actor actor) {
        validateIdempotencyKey(idempotencyKey);

        // 1. 멱등성 검사: 동일 회원, 동일 키 재요청이면 기존 주문 반환 (HTTP 200)
        Optional<Order> existingOrder = orderRepository.findByMemberIdAndIdempotencyKey(actor.memberId(), idempotencyKey);
        if (existingOrder.isPresent()) {
            log.info("Idempotent order request detected: orderId={}, idempotencyKey={}",
                    existingOrder.get().getId(), idempotencyKey);
            return new CreateOrderResult(OrderResponse.from(existingOrder.get()), false);
        }

        // 2. 품목 검증
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_EMPTY);
        }
        if (request.items().size() > 30) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Set<Long> foodIdSet = new HashSet<>();
        for (OrderCreateRequest.OrderItemRequest item : request.items()) {
            if (!foodIdSet.add(item.foodId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_ORDER_ITEM);
            }
        }

        // 3. 카탈로그 스냅샷 동기 호출
        OrderSnapshotResponse snapshot = foodCatalogClient.getOrderSnapshot(request.restaurantId());
        if (!snapshot.active()) {
            throw new BusinessException(ErrorCode.RESTAURANT_CLOSED);
        }

        Map<Long, OrderSnapshotResponse.FoodSnapshotResponse> catalogFoods = snapshot.foods().stream()
                .collect(Collectors.toMap(OrderSnapshotResponse.FoodSnapshotResponse::foodId, f -> f));

        int foodAmount = 0;
        Order order = Order.builder()
                .memberId(actor.memberId())
                .restaurantId(snapshot.restaurantId())
                .restaurantOwnerId(snapshot.ownerId())
                .restaurantName(snapshot.name())
                .fulfillmentStatus(FulfillmentStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .deliveryFee(snapshot.deliveryFee())
                .recipientName(request.recipientName())
                .recipientPhone(request.recipientPhone())
                .address(request.address())
                .requestNote(request.requestNote())
                .idempotencyKey(idempotencyKey)
                .build();

        for (OrderCreateRequest.OrderItemRequest itemReq : request.items()) {
            OrderSnapshotResponse.FoodSnapshotResponse catalogFood = catalogFoods.get(itemReq.foodId());
            if (catalogFood == null) {
                throw new BusinessException(ErrorCode.FOOD_NOT_FOUND);
            }
            if (catalogFood.soldOut()) {
                throw new BusinessException(ErrorCode.FOOD_UNAVAILABLE);
            }

            int lineAmount = catalogFood.price() * itemReq.quantity();
            foodAmount += lineAmount;

            OrderItem orderItem = OrderItem.builder()
                    .foodId(catalogFood.foodId())
                    .foodName(catalogFood.name())
                    .unitPrice(catalogFood.price())
                    .quantity(itemReq.quantity())
                    .lineAmount(lineAmount)
                    .build();

            order.addItem(orderItem);
        }

        if (foodAmount < snapshot.minOrderAmount()) {
            throw new BusinessException(ErrorCode.MIN_ORDER_AMOUNT_NOT_MET);
        }

        // totalAmount = foodAmount + deliveryFee
        int totalAmount = foodAmount + snapshot.deliveryFee();

        // 엔티티에 계산된 금액 주입
        Order finalOrder = Order.builder()
                .memberId(order.getMemberId())
                .restaurantId(order.getRestaurantId())
                .restaurantOwnerId(order.getRestaurantOwnerId())
                .restaurantName(order.getRestaurantName())
                .fulfillmentStatus(order.getFulfillmentStatus())
                .paymentStatus(order.getPaymentStatus())
                .foodAmount(foodAmount)
                .deliveryFee(snapshot.deliveryFee())
                .totalAmount(totalAmount)
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .address(order.getAddress())
                .requestNote(order.getRequestNote())
                .idempotencyKey(order.getIdempotencyKey())
                .build();

        for (OrderItem item : order.getItems()) {
            finalOrder.addItem(item);
        }

        Order savedOrder;
        try {
            savedOrder = orderRepository.save(finalOrder);
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 멱등 키 충돌 처리
            return orderRepository.findByMemberIdAndIdempotencyKey(actor.memberId(), idempotencyKey)
                    .map(o -> new CreateOrderResult(OrderResponse.from(o), false))
                    .orElseThrow(() -> e);
        }

        // 4. Outbox 이벤트 발행 (ORDER_CREATED)
        outboxService.recordOrderCreated(savedOrder);
        log.info("Order created: orderId={}, memberId={}, totalAmount={}",
                savedOrder.getId(), savedOrder.getMemberId(), savedOrder.getTotalAmount());

        return new CreateOrderResult(OrderResponse.from(savedOrder), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(Actor actor, Pageable pageable) {
        Page<Order> page = orderRepository.findByMemberId(actor.memberId(), pageable);
        return PageResponse.of(page, OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(Long orderId, Actor actor) {
        Order order = findOrder(orderId);
        checkViewPermission(order, actor);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getManagedOrders(Long restaurantId,
                                                        FulfillmentStatus status,
                                                        Actor actor,
                                                        Pageable pageable) {
        if (!actor.admin() && !"ROLE_OWNER".equals(actor.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long ownerId = actor.admin() ? null : actor.memberId();
        Page<Order> page = orderRepository.searchManaged(ownerId, restaurantId, status, pageable);
        return PageResponse.of(page, OrderResponse::from);
    }

    @Transactional
    public OrderResponse updateFulfillmentStatus(Long orderId, FulfillmentStatus nextStatus, Actor actor) {
        Order order = findOrder(orderId);
        checkManagePermission(order, actor);

        order.transitionFulfillment(nextStatus);
        log.info("Order status updated: orderId={}, newStatus={}", orderId, nextStatus);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, Actor actor) {
        Order order = findOrder(orderId);

        if (order.isOwnedBy(actor.memberId())) {
            order.cancelByCustomer();
            outboxService.recordOrderCancelled(order, "CUSTOMER");
            log.info("Order cancelled by customer: orderId={}", orderId);
        } else if (order.isManagedBy(actor.memberId())) {
            order.cancelByStaff();
            outboxService.recordOrderCancelled(order, "OWNER");
            log.info("Order cancelled by owner: orderId={}", orderId);
        } else if (actor.admin()) {
            order.cancelByStaff();
            outboxService.recordOrderCancelled(order, "ADMIN");
            log.info("Order cancelled by admin: orderId={}", orderId);
        } else {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse requestRefund(Long orderId, Actor actor) {
        if (!actor.admin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Order order = findOrder(orderId);
        order.requestRefund();
        outboxService.recordOrderRefundRequested(order);
        log.info("Order refund requested by admin: orderId={}", orderId);

        return OrderResponse.from(order);
    }

    @Transactional
    public void processPaymentEvent(EventEnvelope<PaymentResultPayload> envelope) {
        String eventType = envelope.eventType();
        PaymentResultPayload payload = envelope.payload();

        Optional<Order> orderOpt = orderRepository.findById(payload.orderId());
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for payment event: orderId={}, eventType={}", payload.orderId(), eventType);
            return;
        }

        Order order = orderOpt.get();

        if (payload.amount() != order.getTotalAmount()) {
            log.error("Payment amount mismatch in event: orderId={}, expected={}, received={}",
                    order.getId(), order.getTotalAmount(), payload.amount());
            return;
        }

        switch (eventType) {
            case "PAYMENT_COMPLETED" -> {
                if (order.getFulfillmentStatus() == FulfillmentStatus.CANCELLED) {
                    // 이행이 이미 취소된 상태에서 결제 완료 도착 시 환불 요청 발행
                    order.markRefundPending();
                    outboxService.recordOrderRefundRequested(order);
                    log.info("Payment completed on cancelled order; triggered refund: orderId={}", order.getId());
                } else if (order.getPaymentStatus() == PaymentStatus.UNPAID
                        || order.getPaymentStatus() == PaymentStatus.PAYMENT_FAILED) {
                    order.markPaid();
                    log.info("Order marked as PAID: orderId={}", order.getId());
                }
            }
            case "PAYMENT_FAILED" -> {
                if (order.getPaymentStatus() == PaymentStatus.UNPAID) {
                    order.markPaymentFailed();
                    log.info("Order marked as PAYMENT_FAILED: orderId={}", order.getId());
                }
            }
            case "REFUND_COMPLETED" -> {
                order.markRefunded();
                log.info("Order marked as REFUNDED: orderId={}", order.getId());
            }
            case "REFUND_FAILED" -> {
                order.restorePaidAfterRefundFailure();
                log.warn("Refund failed on payment-service; restored order paymentStatus to PAID: orderId={}", order.getId());
            }
            default -> log.debug("Ignored event type in order-service: {}", eventType);
        }
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void checkViewPermission(Order order, Actor actor) {
        if (!actor.admin() && !order.isOwnedBy(actor.memberId()) && !order.isManagedBy(actor.memberId())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
    }

    private void checkManagePermission(Order order, Actor actor) {
        if (!actor.admin() && !order.isManagedBy(actor.memberId())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            UUID.fromString(idempotencyKey.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
