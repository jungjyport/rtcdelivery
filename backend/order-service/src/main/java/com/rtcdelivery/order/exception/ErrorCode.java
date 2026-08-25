package com.rtcdelivery.order.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드와 HTTP 상태의 매핑.
 *
 * <p>상수명이 그대로 응답 {@code message}에 실린다. 사용자에게 보이는 문장은
 * 프론트엔드가 {@code error.{CODE}} i18n 키로 만든다.
 *
 * <p>코드를 추가할 때는 docs/error-handling.md §3 카탈로그와
 * {@code i18n/locales/{ko,ja}.json}에도 함께 등록한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통 ──────────────────────────────────────────────
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    // ── 주문 ──────────────────────────────────────────────
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    ORDER_ITEM_EMPTY(HttpStatus.BAD_REQUEST),
    ORDER_ALREADY_CANCELLED(HttpStatus.CONFLICT),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT),
    INVALID_ORDER_STATUS_TRANSITION(HttpStatus.CONFLICT),

    /** 타 서비스(카탈로그·결제) 호출 실패. Circuit Breaker fallback에서 사용한다. */
    UPSTREAM_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    public String code() {
        return name();
    }
}
