package com.rtcdelivery.gateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Gateway 단계에서 발생하는 비즈니스 규칙 위반.
 * {@link GatewayErrorWebExceptionHandler}가 {@link ErrorCode}에 따라 응답으로 변환한다.
 *
 * <p>하위 서비스의 {@code BusinessException}과 이름을 맞춘다. 참고 문서의 {@code GlobalException}이
 * 이 클래스에 대응한다.
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
