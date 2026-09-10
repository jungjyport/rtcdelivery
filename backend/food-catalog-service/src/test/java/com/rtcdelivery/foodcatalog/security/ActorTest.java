package com.rtcdelivery.foodcatalog.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActorTest {

    private static Authentication authentication(Object principal, String... roles) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }

    @Test
    @DisplayName("from_점주_권한이면_admin은_false다")
    void from_ownerRole_isNotAdmin() {
        Actor actor = Actor.from(authentication(100L, "ROLE_OWNER"));

        assertThat(actor.memberId()).isEqualTo(100L);
        assertThat(actor.admin()).isFalse();
    }

    @Test
    @DisplayName("from_관리자_권한이면_admin은_true다")
    void from_adminRole_isAdmin() {
        Actor actor = Actor.from(authentication(100L, "ROLE_ADMIN"));

        assertThat(actor.admin()).isTrue();
    }

    @Test
    @DisplayName("from_인증이_없으면_memberId가_null이다")
    void from_nullAuthentication_hasNullMemberId() {
        Actor actor = Actor.from(null);

        assertThat(actor.memberId()).isNull();
        assertThat(actor.admin()).isFalse();
    }

    @Test
    @DisplayName("from_principal이_Long이_아니면_memberId가_null이다")
    void from_nonLongPrincipal_hasNullMemberId() {
        Actor actor = Actor.from(authentication("anonymousUser", "ROLE_ANONYMOUS"));

        assertThat(actor.memberId()).isNull();
    }
}
