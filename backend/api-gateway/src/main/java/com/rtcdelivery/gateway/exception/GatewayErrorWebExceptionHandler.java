package com.rtcdelivery.gateway.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Gateway의 모든 미처리 예외를 {@code ApiResponse} 형태의 에러 응답으로 변환한다.
 *
 * <p>Spring Cloud Gateway(Reactive)는 프록시 필터 체인에서 발생한 예외를 {@code @RestControllerAdvice}로
 * 전달하지 않으므로 {@link ErrorWebExceptionHandler}를 구현해야 한다.
 * {@code @Order(-2)}는 기본 핸들러({@code DefaultErrorWebExceptionHandler}, -1)보다 먼저 실행되기 위한 것이다.
 *
 * <p>상세 규약은 docs/error-handling.md §5 참조.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    /** 순환 참조가 있는 예외 체인에서 무한 순회를 막는다. */
    private static final int MAX_CAUSE_DEPTH = 10;

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        ErrorCode errorCode = resolve(ex);
        String path = exchange.getRequest().getPath().value();

        if (errorCode.getStatus().is5xxServerError()) {
            log.error("Gateway 오류 [{}] {}", errorCode.code(), path, ex);
        }
        else {
            log.warn("Gateway 오류 [{}] {} - {}", errorCode.code(), path, ex.getMessage());
        }

        return errorResponseWriter.write(exchange, errorCode);
    }

    private ErrorCode resolve(Throwable ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException.getErrorCode();
        }
        // 인증은 되었으나 권한이 부족한 상황이므로 403이다.
        // 401로 내리면 프론트가 토큰 갱신을 시도했다가 다시 거부당하는 왕복이 생긴다.
        if (ex instanceof AccessDeniedException || ex instanceof AuthorizationDeniedException) {
            return ErrorCode.FORBIDDEN;
        }
        // 라우팅 대상 인스턴스를 찾지 못한 경우(NotFoundException)도 여기에 해당한다.
        if (ex instanceof ResponseStatusException responseStatusException) {
            return fromStatus(responseStatusException.getStatusCode());
        }
        if (hasCause(ex, ConnectException.class)) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }
        if (hasCause(ex, TimeoutException.class)) {
            return ErrorCode.GATEWAY_TIMEOUT;
        }
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }

    private ErrorCode fromStatus(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return ErrorCode.INTERNAL_SERVER_ERROR;
        }
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.INVALID_PARAMETER;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.ENDPOINT_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case TOO_MANY_REQUESTS -> ErrorCode.RATE_LIMIT_EXCEEDED;
            case SERVICE_UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
            case GATEWAY_TIMEOUT -> ErrorCode.GATEWAY_TIMEOUT;
            default -> ErrorCode.INTERNAL_SERVER_ERROR;
        };
    }

    private boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
