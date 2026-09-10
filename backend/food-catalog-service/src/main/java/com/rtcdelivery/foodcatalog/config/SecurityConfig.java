package com.rtcdelivery.foodcatalog.config;

import com.rtcdelivery.foodcatalog.exception.RestAccessDeniedHandler;
import com.rtcdelivery.foodcatalog.exception.RestAuthenticationEntryPoint;
import com.rtcdelivery.foodcatalog.security.HeaderAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 인가를 두 단계로 나눈다.
 *
 * <ol>
 *   <li>필터 레벨({@code authorizeHttpRequests}) — 조회는 공개, 쓰기는 인증 필요. 비로그인 요청을
 *       여기서 401로 끊는다.</li>
 *   <li>메서드 레벨({@code @PreAuthorize}) — 역할 검사.</li>
 * </ol>
 *
 * <p>역할 검사를 메서드 레벨에만 두면 익명 사용자도 403을 받는다. {@code GlobalExceptionHandler}가
 * {@code AccessDeniedException}을 먼저 잡아 {@code ExceptionTranslationFilter}의 익명/인증 구분이
 * 적용되지 않기 때문이다.
 *
 * <p>{@code RoleHierarchy}는 쓰지 않는다. "로그인한 사용자 전체"는 {@code isAuthenticated()}로,
 * 역할이 특정되는 자리는 {@code hasAnyRole(...)}로 명시한다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final HeaderAuthenticationFilter headerAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                        // 카탈로그 조회는 비로그인 사용자에게도 열려 있다
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/categories/**",
                                "/api/v1/restaurants/**",
                                "/api/v1/foods/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
