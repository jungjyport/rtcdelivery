package com.rtcdelivery.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.gateway.exception.ErrorCode;
import com.rtcdelivery.gateway.exception.ErrorResponseWriter;
import com.rtcdelivery.gateway.security.JwtValidator;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtVerificationFilterTest {

    @Mock
    private JwtValidator jwtValidator;

    @Mock
    private GatewayFilterChain filterChain;

    private ErrorResponseWriter errorResponseWriter;
    private JwtVerificationFilter filter;

    @BeforeEach
    void setUp() {
        errorResponseWriter = new ErrorResponseWriter(new ObjectMapper());
        filter = new JwtVerificationFilter(jwtValidator, errorResponseWriter);
    }

    @Test
    @DisplayName("제외 경로(/api/v1/auth/signup)는 토큰 없이도 통과한다")
    void filter_제외경로_토큰없이통과() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/signup").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        given(filterChain.filter(any(ServerWebExchange.class))).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(any(ServerWebExchange.class));
        verify(jwtValidator, never()).validate(any());
    }

    @Test
    @DisplayName("OPTIONS 요청은 토큰 없이도 통과한다 (CORS Preflight)")
    void filter_OPTIONS요청_토큰없이통과() {
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/v1/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        given(filterChain.filter(any(ServerWebExchange.class))).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(any(ServerWebExchange.class));
        verify(jwtValidator, never()).validate(any());
    }

    @Test
    @DisplayName("유효한 토큰 전달 시 X-User-* 헤더가 주입되어 체인이 실행된다")
    void filter_유효토큰_헤더주입() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_jwt_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Claims claims = mock(Claims.class);
        given(claims.get(JwtValidator.CLAIM_USER_ID)).willReturn(1L);
        given(claims.getSubject()).willReturn("hong_gildong");
        given(claims.get(JwtValidator.CLAIM_ROLE)).willReturn("ROLE_USER");

        given(jwtValidator.validate("valid_jwt_token")).willReturn(JwtValidator.ValidationResult.success(claims));
        given(filterChain.filter(any(ServerWebExchange.class))).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(filterChain).filter(exchangeCaptor.capture());

        ServerWebExchange forwarded = exchangeCaptor.getValue();
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("1");
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Name")).isEqualTo("hong_gildong");
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("클라이언트가 위조한 X-User-* 헤더는 유효한 토큰이어도 덮어써지고 이전 위조값은 제거된다")
    void filter_클라이언트가_XUserRole위조_제거됨() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_jwt_token")
                .header("X-User-Role", "ROLE_ADMIN") // 클라이언트의 위조 헤더
                .header("X-User-Id", "999")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Claims claims = mock(Claims.class);
        given(claims.get(JwtValidator.CLAIM_USER_ID)).willReturn(1L);
        given(claims.getSubject()).willReturn("hong_gildong");
        given(claims.get(JwtValidator.CLAIM_ROLE)).willReturn("ROLE_USER"); // 실제 클레임

        given(jwtValidator.validate("valid_jwt_token")).willReturn(JwtValidator.ValidationResult.success(claims));
        given(filterChain.filter(any(ServerWebExchange.class))).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(filterChain).filter(exchangeCaptor.capture());

        ServerWebExchange forwarded = exchangeCaptor.getValue();
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("1");
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("제외 경로에서도 클라이언트가 보낸 위조 헤더가 제거된다")
    void filter_제외경로에서도_위조헤더제거됨() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login")
                .header("X-User-Role", "ROLE_ADMIN")
                .header("X-User-Id", "999")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        given(filterChain.filter(any(ServerWebExchange.class))).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(filterChain).filter(exchangeCaptor.capture());

        ServerWebExchange forwarded = exchangeCaptor.getValue();
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Role")).isNull();
    }

    @Test
    @DisplayName("인증이 필요한 경로에 토큰이 없으면 401 ACCESS_TOKEN_INVALID 응답이 작성된다")
    void filter_토큰없음_401_ApiResponse형식() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("만료된 토큰인 경우 401 ACCESS_TOKEN_EXPIRED 응답이 작성된다")
    void filter_만료토큰_401_ACCESS_TOKEN_EXPIRED() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired_jwt_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(jwtValidator.validate("expired_jwt_token"))
                .willReturn(JwtValidator.ValidationResult.failure(ErrorCode.ACCESS_TOKEN_EXPIRED));

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
