package com.rtcdelivery.payment.security;

import com.rtcdelivery.payment.domain.Role;
import com.rtcdelivery.payment.exception.BusinessException;
import com.rtcdelivery.payment.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorTest {

    @Test
    @DisplayName("Actor 헬퍼 메서드 역할 판별 검증")
    void roleChecks() {
        Actor user = new Actor(1L, Role.ROLE_USER);
        assertThat(user.isUser()).isTrue();
        assertThat(user.isOwner()).isFalse();
        assertThat(user.isAdmin()).isFalse();

        Actor owner = new Actor(2L, Role.ROLE_OWNER);
        assertThat(owner.isUser()).isFalse();
        assertThat(owner.isOwner()).isTrue();
        assertThat(owner.isAdmin()).isFalse();

        Actor admin = new Actor(3L, Role.ROLE_ADMIN);
        assertThat(admin.isUser()).isFalse();
        assertThat(admin.isOwner()).isFalse();
        assertThat(admin.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("Authentication에서 Actor 추출 성공")
    void from_success() {
        Actor actor = new Actor(10L, Role.ROLE_USER);
        Authentication auth = new UsernamePasswordAuthenticationToken(actor, null, Collections.emptyList());

        Actor extracted = Actor.from(auth);
        assertThat(extracted).isEqualTo(actor);
    }

    @Test
    @DisplayName("Authentication이 null이거나 principal이 Actor가 아니면 UNAUTHORIZED 예외")
    void from_failure() {
        assertThatThrownBy(() -> Actor.from(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        Authentication invalidAuth = new UsernamePasswordAuthenticationToken("anonymousUser", null);
        assertThatThrownBy(() -> Actor.from(invalidAuth))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
