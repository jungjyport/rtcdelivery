package com.rtcdelivery.foodcatalog.security;

import com.rtcdelivery.foodcatalog.domain.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * 요청을 수행한 주체. {@link HeaderAuthenticationFilter}가 세운 {@code Authentication}에서 뽑아낸다.
 *
 * <p>역할 자체의 판정은 컨트롤러의 {@code @PreAuthorize}가 하고, 이 타입은 서비스 계층의
 * 소유권 검증에만 쓰인다.
 */
public record Actor(Long memberId, boolean admin) {

    public static Actor from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long memberId)) {
            return new Actor(null, false);
        }

        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Role.ROLE_ADMIN.name()::equals);

        return new Actor(memberId, admin);
    }
}
