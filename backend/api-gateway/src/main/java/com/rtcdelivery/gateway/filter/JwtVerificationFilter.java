package com.rtcdelivery.gateway.filter;

import com.rtcdelivery.gateway.exception.ErrorCode;
import com.rtcdelivery.gateway.exception.ErrorResponseWriter;
import com.rtcdelivery.gateway.security.JwtValidator;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtVerificationFilter implements GlobalFilter, Ordered {

    public static final int FILTER_ORDER = -10;

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private static final List<String> EXCLUDE_PATH_PATTERNS = List.of(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/logout",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**"
    );

    private final JwtValidator jwtValidator;
    private final ErrorResponseWriter errorResponseWriter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. 클라이언트가 보낸 위조 가능 헤더(X-User-*)를 무조건 제거
        ServerHttpRequest cleanRequest = request.mutate()
                .headers(httpHeaders -> {
                    httpHeaders.remove(HEADER_USER_ID);
                    httpHeaders.remove(HEADER_USER_NAME);
                    httpHeaders.remove(HEADER_USER_ROLE);
                })
                .build();
        ServerWebExchange cleanExchange = exchange.mutate().request(cleanRequest).build();

        // 2. 제외 경로 및 Preflight(OPTIONS) 확인
        if (isExcluded(cleanRequest)) {
            return chain.filter(cleanExchange);
        }

        // 3. Authorization: Bearer <token> 추출
        String authHeader = cleanRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Missing or invalid Authorization header");
            return errorResponseWriter.write(cleanExchange, ErrorCode.ACCESS_TOKEN_INVALID);
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            log.debug("Empty Bearer token");
            return errorResponseWriter.write(cleanExchange, ErrorCode.ACCESS_TOKEN_INVALID);
        }

        // 4. JWT 검증
        JwtValidator.ValidationResult result = jwtValidator.validate(token);
        if (!result.isValid()) {
            return errorResponseWriter.write(cleanExchange, result.errorCode());
        }

        // 5. 클레임에서 사용자 정보 추출
        Claims claims = result.claims();
        Object userIdObj = claims.get(JwtValidator.CLAIM_USER_ID);
        String userId = userIdObj != null ? String.valueOf(userIdObj) : "";
        String username = claims.getSubject() != null ? claims.getSubject() : "";
        String role = (String) claims.get(JwtValidator.CLAIM_ROLE);

        // 6. ServerHttpRequest.mutate()로 X-User-* 주입
        ServerHttpRequest mutatedRequest = cleanRequest.mutate()
                .header(HEADER_USER_ID, userId)
                .header(HEADER_USER_NAME, username)
                .header(HEADER_USER_ROLE, role != null ? role : "")
                .build();

        log.debug("Injected headers: X-User-Id={}, X-User-Name={}, X-User-Role={}", userId, username, role);

        // 7. 다음 체인 실행
        return chain.filter(cleanExchange.mutate().request(mutatedRequest).build());
    }

    private boolean isExcluded(ServerHttpRequest request) {
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return true;
        }

        String path = request.getURI().getPath();
        for (String pattern : EXCLUDE_PATH_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
