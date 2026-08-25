package com.rtcdelivery.payment.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.payment.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security 필터 단계에서 발생한 에러를 {@link ApiResponse} 형태로 직접 써 내려간다.
 *
 * <p>필터는 {@code @RestControllerAdvice}보다 앞에서 동작하므로 응답을 스스로 만들어야 한다.
 */
final class ErrorResponseWriter {

    private ErrorResponseWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
            throws IOException {
        HttpStatus status = errorCode.getStatus();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.<Void>error(status.value(), errorCode.code()));
    }
}
