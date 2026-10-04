package com.rtcdelivery.payment.security;

import com.rtcdelivery.payment.domain.Role;
import com.rtcdelivery.payment.exception.BusinessException;
import com.rtcdelivery.payment.exception.ErrorCode;
import org.springframework.security.core.Authentication;

public record Actor(Long id, Role role) {

    public static Actor from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Actor actor)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return actor;
    }

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN;
    }

    public boolean isOwner() {
        return role == Role.ROLE_OWNER;
    }

    public boolean isUser() {
        return role == Role.ROLE_USER;
    }
}
