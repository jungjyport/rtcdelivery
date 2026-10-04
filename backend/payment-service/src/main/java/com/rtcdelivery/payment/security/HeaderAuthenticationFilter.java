package com.rtcdelivery.payment.security;

import com.rtcdelivery.payment.domain.Role;
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

@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String userIdHeader = request.getHeader(HEADER_USER_ID);
        String userRoleHeader = request.getHeader(HEADER_USER_ROLE);

        if (userIdHeader != null && userRoleHeader != null) {
            try {
                Long userId = Long.parseLong(userIdHeader.trim());
                Role role = Role.valueOf(userRoleHeader.trim());

                Actor actor = new Actor(userId, role);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        actor,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(role.name()))
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.warn("Invalid gateway user headers: id={}, role={}, error={}",
                        userIdHeader, userRoleHeader, e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
