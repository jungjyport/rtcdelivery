package com.rtcdelivery.translation.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

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
