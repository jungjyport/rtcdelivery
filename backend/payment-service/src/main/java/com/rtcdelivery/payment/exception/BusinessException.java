package com.rtcdelivery.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 비즈?�스 로직 커스?� ?�외
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
