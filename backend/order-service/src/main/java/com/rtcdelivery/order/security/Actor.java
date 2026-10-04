package com.rtcdelivery.order.security;

import com.rtcdelivery.order.domain.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record Actor(Long memberId, boolean admin, String role) {

    public static Actor from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long memberId)) {
            return new Actor(null, false, null);
        }

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse(null);

        boolean admin = Role.ROLE_ADMIN.name().equals(role);

        return new Actor(memberId, admin, role);
    }
}
