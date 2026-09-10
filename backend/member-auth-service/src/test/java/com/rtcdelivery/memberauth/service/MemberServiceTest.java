package com.rtcdelivery.memberauth.service;

import com.rtcdelivery.memberauth.domain.AuthProvider;
import com.rtcdelivery.memberauth.domain.Member;
import com.rtcdelivery.memberauth.domain.Role;
import com.rtcdelivery.memberauth.dto.response.MemberResponse;
import com.rtcdelivery.memberauth.exception.BusinessException;
import com.rtcdelivery.memberauth.exception.ErrorCode;
import com.rtcdelivery.memberauth.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Long TARGET_ID = 1L;
    private static final Long ADMIN_ID = 2L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private MemberService memberService;

    private Member target;

    @BeforeEach
    void setUp() {
        target = Member.builder()
                .id(TARGET_ID)
                .username("hong_gildong")
                .password("encoded")
                .nickname("홍길동")
                .role(Role.ROLE_USER)
                .authProvider(AuthProvider.LOCAL)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("changeRole_점주로_승격하고_Refresh_Token을_폐기한다")
    void changeRole_promoteToOwner_invalidatesRefreshToken() {
        given(memberRepository.findById(TARGET_ID)).willReturn(Optional.of(target));

        MemberResponse result = memberService.changeRole(TARGET_ID, Role.ROLE_OWNER, ADMIN_ID);

        assertThat(result.getRole()).isEqualTo(Role.ROLE_OWNER);
        assertThat(target.getRole()).isEqualTo(Role.ROLE_OWNER);
        verify(refreshTokenService).invalidateByUsername("hong_gildong");
    }

    @Test
    @DisplayName("changeRole_같은_역할이면_Refresh_Token을_건드리지_않는다")
    void changeRole_sameRole_doesNotInvalidateSession() {
        given(memberRepository.findById(TARGET_ID)).willReturn(Optional.of(target));

        memberService.changeRole(TARGET_ID, Role.ROLE_USER, ADMIN_ID);

        verify(refreshTokenService, never()).invalidateByUsername("hong_gildong");
    }

    @Test
    @DisplayName("changeRole_자기_자신의_역할은_바꿀_수_없다")
    void changeRole_self_throwsCannotChangeOwnRole() {
        assertThatThrownBy(() -> memberService.changeRole(ADMIN_ID, Role.ROLE_USER, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_CHANGE_OWN_ROLE);

        verify(memberRepository, never()).findById(ADMIN_ID);
    }

    @Test
    @DisplayName("changeRole_존재하지_않는_회원은_MEMBER_NOT_FOUND")
    void changeRole_unknownMember_throwsNotFound() {
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.changeRole(99L, Role.ROLE_OWNER, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
