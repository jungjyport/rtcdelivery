package com.rtcdelivery.gateway.exception;

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
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    /** 다운스트림 서비스 인스턴스를 찾지 못했거나 연결에 실패했다. */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    /** 다운스트림 응답이 제한 시간 내에 도착하지 않았다. */
    GATEWAY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),

    // ── 토큰 ──────────────────────────────────────────────
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED);

    private final HttpStatus status;

    public String code() {
        return name();
    }
}
