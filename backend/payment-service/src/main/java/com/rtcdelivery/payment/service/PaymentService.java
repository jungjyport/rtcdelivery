package com.rtcdelivery.payment.service;

import com.rtcdelivery.payment.client.MockPaymentGateway;
import com.rtcdelivery.payment.domain.Payment;
import com.rtcdelivery.payment.domain.PaymentStatus;
import com.rtcdelivery.payment.dto.event.EventEnvelope;
import com.rtcdelivery.payment.dto.event.OrderCancelledPayload;
import com.rtcdelivery.payment.dto.event.OrderCreatedPayload;
import com.rtcdelivery.payment.dto.event.OrderRefundRequestedPayload;
import com.rtcdelivery.payment.dto.event.PaymentResultPayload;
import com.rtcdelivery.payment.dto.request.PaymentApproveRequest;
import com.rtcdelivery.payment.dto.response.PaymentResponse;
import com.rtcdelivery.payment.exception.BusinessException;
import com.rtcdelivery.payment.exception.ErrorCode;
import com.rtcdelivery.payment.repository.PaymentRepository;
import com.rtcdelivery.payment.security.Actor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final String TOPIC_PAYMENT_EVENTS = "payment-events";

    private final PaymentRepository paymentRepository;
    private final MockPaymentGateway paymentGateway;
    private final OutboxService outboxService;

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(Long orderId, Actor actor) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!actor.isAdmin() && !actor.isOwner() && !payment.getMemberId().equals(actor.id())) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse approvePayment(Long paymentId, PaymentApproveRequest request, Actor actor) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!actor.isAdmin() && !payment.getMemberId().equals(actor.id())) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Payment already completed, returning idempotent success: paymentId={}", paymentId);
            return PaymentResponse.from(payment);
        }

        if (payment.getStatus() != PaymentStatus.AWAITING) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_APPROVABLE);
        }

        MockPaymentGateway.ApproveResult result = paymentGateway.approve(
                payment.getOrderId(),
                payment.getAmount(),
                request.cardLast4()
        );

        if (result.success()) {
            payment.markCompleted(result.approvalCode(), request.cardLast4());

            EventEnvelope<PaymentResultPayload> envelope = EventEnvelope.of(
                    UUID.randomUUID().toString(),
                    "PAYMENT_COMPLETED",
                    "Payment",
                    payment.getId().toString(),
                    new PaymentResultPayload(payment.getId(), payment.getOrderId(), payment.getMemberId(), payment.getAmount(), null)
            );
            outboxService.recordEvent("Payment", payment.getId().toString(), "PAYMENT_COMPLETED",
                    TOPIC_PAYMENT_EVENTS, payment.getOrderId().toString(), envelope);

            return PaymentResponse.from(payment);
        } else {
            payment.markFailed(result.failureReason());

            EventEnvelope<PaymentResultPayload> envelope = EventEnvelope.of(
                    UUID.randomUUID().toString(),
                    "PAYMENT_FAILED",
                    "Payment",
                    payment.getId().toString(),
                    new PaymentResultPayload(payment.getId(), payment.getOrderId(), payment.getMemberId(), payment.getAmount(), result.failureReason())
            );
            outboxService.recordEvent("Payment", payment.getId().toString(), "PAYMENT_FAILED",
                    TOPIC_PAYMENT_EVENTS, payment.getOrderId().toString(), envelope);

            throw new BusinessException(ErrorCode.PAYMENT_FAILED, result.failureReason());
        }
    }

    @Transactional
    public void handleOrderCreated(OrderCreatedPayload payload) {
        if (paymentRepository.existsByOrderId(payload.orderId())) {
            log.info("Payment already exists for orderId={}, ignoring ORDER_CREATED", payload.orderId());
            return;
        }

        Payment payment = Payment.builder()
                .orderId(payload.orderId())
                .memberId(payload.memberId())
                .amount(payload.totalAmount())
                .status(PaymentStatus.AWAITING)
                .currency("KRW")
                .method("CARD")
                .pgProvider("MOCK")
                .build();

        paymentRepository.save(payment);
        log.info("Created payment for orderId={}: id={}, amount={}", payload.orderId(), payment.getId(), payload.totalAmount());
    }

    @Transactional
    public void handleOrderCancelled(OrderCancelledPayload payload) {
        Payment payment = paymentRepository.findByOrderId(payload.orderId()).orElse(null);
        if (payment == null) {
            log.warn("Payment not found for cancelled orderId={}", payload.orderId());
            return;
        }

        if (payment.getStatus() == PaymentStatus.AWAITING) {
            payment.markCancelled(payload.reason());
            log.info("Payment marked cancelled before approval: orderId={}", payload.orderId());

            EventEnvelope<PaymentResultPayload> envelope = EventEnvelope.of(
                    UUID.randomUUID().toString(),
                    "PAYMENT_FAILED",
                    "Payment",
                    payment.getId().toString(),
                    new PaymentResultPayload(payment.getId(), payment.getOrderId(), payment.getMemberId(), payment.getAmount(), "ORDER_CANCELLED")
            );
            outboxService.recordEvent("Payment", payment.getId().toString(), "PAYMENT_FAILED",
                    TOPIC_PAYMENT_EVENTS, payment.getOrderId().toString(), envelope);

        } else if (payment.getStatus() == PaymentStatus.COMPLETED) {
            paymentGateway.refund(payment.getOrderId(), payment.getPgApprovalCode(), payment.getAmount());
            payment.markRefunded(payload.reason());
            log.info("Payment refunded due to order cancellation: orderId={}", payload.orderId());

            EventEnvelope<PaymentResultPayload> envelope = EventEnvelope.of(
                    UUID.randomUUID().toString(),
                    "PAYMENT_REFUNDED",
                    "Payment",
                    payment.getId().toString(),
                    new PaymentResultPayload(payment.getId(), payment.getOrderId(), payment.getMemberId(), payment.getAmount(), payload.reason())
            );
            outboxService.recordEvent("Payment", payment.getId().toString(), "PAYMENT_REFUNDED",
                    TOPIC_PAYMENT_EVENTS, payment.getOrderId().toString(), envelope);
        } else {
            log.info("Payment for orderId={} in status {}, no refund needed", payload.orderId(), payment.getStatus());
        }
    }

    @Transactional
    public void handleOrderRefundRequested(OrderRefundRequestedPayload payload) {
        Payment payment = paymentRepository.findByOrderId(payload.orderId()).orElse(null);
        if (payment == null) {
            log.warn("Payment not found for refund requested orderId={}", payload.orderId());
            return;
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            paymentGateway.refund(payment.getOrderId(), payment.getPgApprovalCode(), payment.getAmount());
            payment.markRefunded(payload.reason());
            log.info("Payment refunded upon request: orderId={}", payload.orderId());

            EventEnvelope<PaymentResultPayload> envelope = EventEnvelope.of(
                    UUID.randomUUID().toString(),
                    "PAYMENT_REFUNDED",
                    "Payment",
                    payment.getId().toString(),
                    new PaymentResultPayload(payment.getId(), payment.getOrderId(), payment.getMemberId(), payment.getAmount(), payload.reason())
            );
            outboxService.recordEvent("Payment", payment.getId().toString(), "PAYMENT_REFUNDED",
                    TOPIC_PAYMENT_EVENTS, payment.getOrderId().toString(), envelope);
        } else if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment already refunded for orderId={}", payload.orderId());
        } else {
            log.warn("Payment for orderId={} in status {}, cannot refund", payload.orderId(), payment.getStatus());
        }
    }
}
