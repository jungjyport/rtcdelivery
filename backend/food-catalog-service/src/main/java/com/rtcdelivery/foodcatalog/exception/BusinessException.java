package com.rtcdelivery.foodcatalog.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 규칙 위반. {@link GlobalExceptionHandler}가 {@link ErrorCode}에 따라 응답으로 변환한다.
 *
 * <pre>
 * throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 로그에만 남기는 부가 정보. 응답 본문에는 포함하지 않는다. */
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        // 예상된 흐름이므로 스택 트레이스를 수집하지 않는다.
        super(errorCode.name(), null, false, false);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
