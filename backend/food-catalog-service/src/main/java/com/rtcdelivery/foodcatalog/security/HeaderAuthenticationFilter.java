package com.rtcdelivery.foodcatalog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * API Gateway가 JWT를 검증한 뒤 주입한 {@code X-User-*} 헤더를 {@code SecurityContext}로 옮긴다.
 *
 * <p>이 필터가 없으면 {@code SecurityContext}가 비어 있어 {@code @PreAuthorize}가 모든 요청을
 * 거부한다. Gateway는 클라이언트가 보낸 {@code X-User-*}를 항상 제거한 뒤 자신이 검증한 값으로
 * 덮어쓰므로, 이 서비스는 헤더를 신뢰한다. 그래서 운영에서 8081 포트를 외부에 노출하면 안 된다.
 */
@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_NAME = "X-User-Name";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String xUserId = request.getHeader(HEADER_USER_ID);

        if (xUserId != null && !xUserId.isBlank()) {
            try {
                Long userId = Long.parseLong(xUserId.trim());
                String xUserRole = request.getHeader(HEADER_USER_ROLE);

                List<SimpleGrantedAuthority> authorities = Collections.emptyList();
                if (xUserRole != null && !xUserRole.isBlank()) {
                    authorities = List.of(new SimpleGrantedAuthority(xUserRole.trim()));
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user from gateway headers: userId={}, role={}", userId, xUserRole);
            } catch (NumberFormatException e) {
                log.warn("Invalid X-User-Id header format: {}", xUserId);
            }
        }

        filterChain.doFilter(request, response);
    }
}
