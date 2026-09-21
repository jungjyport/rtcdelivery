package com.rtcdelivery.translation.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * translation-service 에러 코드와 HTTP 상태의 매핑.
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

    // ── 번역 ──────────────────────────────────────────────
    TRANSLATION_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    TRANSLATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    TRANSLATION_TEXT_TOO_LONG(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_TARGET_LOCALE(HttpStatus.BAD_REQUEST),
    TRANSLATION_JOB_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSLATION_JOB_NOT_RETRYABLE(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    public String code() {
        return name();
    }
}
