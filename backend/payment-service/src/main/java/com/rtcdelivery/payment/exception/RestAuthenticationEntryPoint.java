package com.rtcdelivery.payment.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증되지 않은 요청에 대한 진입점. 기본 동작(로그인 폼 리디렉트 / WWW-Authenticate 헤더) 대신
 * {@code ApiResponse} 형태의 401을 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("인증되지 않은 접근: {} {}", request.getMethod(), request.getRequestURI());
        ErrorResponseWriter.write(response, objectMapper, ErrorCode.UNAUTHORIZED);
    }
}
