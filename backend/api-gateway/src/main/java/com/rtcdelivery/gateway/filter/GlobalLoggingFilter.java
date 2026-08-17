package com.rtcdelivery.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 글로벌 로깅 필터
 * 모든 요청/응답에 대한 로깅을 수행합니다.
 */
@Slf4j
@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestPath = exchange.getRequest().getPath().toString();
        String method = exchange.getRequest().getMethod().toString();
        log.info(">>> Gateway Request: {} {}", method, requestPath);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;
            log.info("<<< Gateway Response: {} {} - Status: {}", method, requestPath, statusCode);
        }));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
