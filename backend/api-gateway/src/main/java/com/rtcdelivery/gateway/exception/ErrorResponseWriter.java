package com.rtcdelivery.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.gateway.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 에러 응답 본문을 {@link ApiResponse} 형태로 직접 써 내려간다.
 *
 * <p>{@link GatewayErrorWebExceptionHandler}와 JWT 검증 필터가 이 클래스를 공유해
 * 두 경로의 응답 포맷이 갈라지지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode errorCode) {
        return write(exchange, errorCode.getStatus(), errorCode.code());
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code) {
        ServerHttpResponse response = exchange.getResponse();

        // 본문 전송이 시작된 뒤에는 상태 코드와 헤더를 바꿀 수 없다.
        if (response.isCommitted()) {
            log.warn("응답이 이미 커밋되어 에러 본문을 쓰지 못했다: {}", code);
            return Mono.empty();
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = response.bufferFactory().wrap(serialize(status, code));
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] serialize(HttpStatus status, String code) {
        try {
            return objectMapper.writeValueAsBytes(ApiResponse.error(status.value(), code));
        }
        catch (JsonProcessingException e) {
            // 직렬화가 실패해도 클라이언트는 파싱 가능한 응답을 받아야 한다.
            log.error("에러 응답 직렬화 실패", e);
            return ("{\"status\":" + status.value()
                    + ",\"message\":\"" + ErrorCode.INTERNAL_SERVER_ERROR.code()
                    + "\",\"data\":null}").getBytes(StandardCharsets.UTF_8);
        }
    }
}
