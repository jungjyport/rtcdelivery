package com.rtcdelivery.order.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActorTest {

    @Test
    @DisplayName("from_인증_객체가_null이면_빈_Actor를_반환한다")
    void from_nullAuthentication_returnsEmpty() {
        Actor actor = Actor.from(null);
        assertThat(actor.memberId()).isNull();
        assertThat(actor.admin()).isFalse();
    }

    @Test
    @DisplayName("from_일반_사용자_인증_객체에서_Actor를_생성한다")
    void from_userRole_returnsActor() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                10L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        Actor actor = Actor.from(auth);
        assertThat(actor.memberId()).isEqualTo(10L);
        assertThat(actor.admin()).isFalse();
        assertThat(actor.role()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("from_관리자_역할이면_admin이_true다")
    void from_adminRole_returnsAdminTrue() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        Actor actor = Actor.from(auth);
        assertThat(actor.memberId()).isEqualTo(1L);
        assertThat(actor.admin()).isTrue();
        assertThat(actor.role()).isEqualTo("ROLE_ADMIN");
    }
}
