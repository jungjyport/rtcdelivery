package com.rtcdelivery.memberauth.exception;

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

    // ── 회원 ──────────────────────────────────────────────
    DUPLICATE_USERNAME(HttpStatus.CONFLICT),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND),
    MEMBER_INACTIVE(HttpStatus.FORBIDDEN),

    /**
     * 아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다.
     * 구분해 내려주면 공격자가 유효한 아이디를 열거할 수 있다.
     */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    // ── 토큰 ──────────────────────────────────────────────
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED);

    private final HttpStatus status;

    public String code() {
        return name();
    }
}
