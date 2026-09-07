package com.rtcdelivery.memberauth.service;

import com.rtcdelivery.memberauth.domain.AuthProvider;
import com.rtcdelivery.memberauth.domain.Member;
import com.rtcdelivery.memberauth.domain.Role;
import com.rtcdelivery.memberauth.dto.request.LoginRequest;
import com.rtcdelivery.memberauth.dto.request.SignupRequest;
import com.rtcdelivery.memberauth.dto.response.MemberResponse;
import com.rtcdelivery.memberauth.exception.BusinessException;
import com.rtcdelivery.memberauth.exception.ErrorCode;
import com.rtcdelivery.memberauth.repository.MemberRepository;
import com.rtcdelivery.memberauth.security.JwtProperties;
import com.rtcdelivery.memberauth.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .id(1L)
                .username("hong_gildong")
                .password("encoded_pass")
                .nickname("홍길동")
                .email("hong@example.com")
                .role(Role.ROLE_USER)
                .authProvider(AuthProvider.LOCAL)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("회원가입 정상 입력 시 비밀번호가 해시되어 저장되고 회원 정보가 반환된다")
    void signup_정상입력_회원저장() {
        SignupRequest request = SignupRequest.builder()
                .username("hong_gildong")
                .password("Passw0rd!")
                .nickname("홍길동")
                .email("hong@example.com")
                .build();

        given(memberRepository.existsByUsername(request.getUsername())).willReturn(false);
        given(memberRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(memberRepository.existsByNickname(request.getNickname())).willReturn(false);
        given(passwordEncoder.encode(request.getPassword())).willReturn("encoded_pass");
        given(memberRepository.save(any(Member.class))).willReturn(testMember);

        MemberResponse response = authService.signup(request);

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("hong_gildong");
        assertThat(response.getNickname()).isEqualTo("홍길동");

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getPassword()).isEqualTo("encoded_pass");
    }

    @Test
    @DisplayName("중복 아이디로 가입 시 DUPLICATE_USERNAME 예외가 발생한다")
    void signup_중복아이디_DUPLICATE_USERNAME() {
        SignupRequest request = SignupRequest.builder()
                .username("hong_gildong")
                .password("Passw0rd!")
                .nickname("홍길동")
                .email("hong@example.com")
                .build();

        given(memberRepository.existsByUsername(request.getUsername())).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_USERNAME));
    }

    @Test
    @DisplayName("중복 이메일로 가입 시 DUPLICATE_EMAIL 예외가 발생한다")
    void signup_중복이메일_DUPLICATE_EMAIL() {
        SignupRequest request = SignupRequest.builder()
                .username("hong_gildong")
                .password("Passw0rd!")
                .nickname("홍길동")
                .email("hong@example.com")
                .build();

        given(memberRepository.existsByUsername(request.getUsername())).willReturn(false);
        given(memberRepository.existsByEmail(request.getEmail())).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
    }

    @Test
    @DisplayName("정상 자격 증명으로 로그인 시 Access Token과 Refresh Token이 발급되고 Redis에 저장된다")
    void login_정상자격증명_토큰발급() {
        LoginRequest request = LoginRequest.builder()
                .username("hong_gildong")
                .password("Passw0rd!")
                .build();

        given(memberRepository.findByUsername(request.getUsername())).willReturn(Optional.of(testMember));
        given(passwordEncoder.matches(request.getPassword(), testMember.getPassword())).willReturn(true);
        given(jwtProvider.createAccessToken(testMember)).willReturn("mock_access_token");
        given(jwtProvider.createRefreshToken(testMember)).willReturn("mock_refresh_token");
        given(jwtProperties.getAccessTokenValidity()).willReturn(1800000L);

        AuthService.AuthLoginResult result = authService.login(request);

        assertThat(result.loginResponse().getAccessToken()).isEqualTo("mock_access_token");
        assertThat(result.loginResponse().getExpiresIn()).isEqualTo(1800);
        assertThat(result.refreshToken()).isEqualTo("mock_refresh_token");

        verify(refreshTokenService).saveRefreshToken("hong_gildong", "mock_refresh_token");
    }

    @Test
    @DisplayName("존재하지 않는 아이디로 로그인 시 INVALID_CREDENTIALS 예외가 발생한다")
    void login_없는아이디_INVALID_CREDENTIALS() {
        LoginRequest request = LoginRequest.builder()
                .username("unknown_user")
                .password("Passw0rd!")
                .build();

        given(memberRepository.findByUsername(request.getUsername())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(passwordEncoder).matches(eq("Passw0rd!"), anyString());
    }

    @Test
    @DisplayName("비밀번호 불일치 시 INVALID_CREDENTIALS 예외가 발생한다")
    void login_비밀번호불일치_INVALID_CREDENTIALS() {
        LoginRequest request = LoginRequest.builder()
                .username("hong_gildong")
                .password("WrongPass!")
                .build();

        given(memberRepository.findByUsername(request.getUsername())).willReturn(Optional.of(testMember));
        given(passwordEncoder.matches(request.getPassword(), testMember.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("비활성 계정으로 로그인 시 MEMBER_INACTIVE 예외가 발생한다")
    void login_비활성계정_MEMBER_INACTIVE() {
        testMember.deactivate();

        LoginRequest request = LoginRequest.builder()
                .username("hong_gildong")
                .password("Passw0rd!")
                .build();

        given(memberRepository.findByUsername(request.getUsername())).willReturn(Optional.of(testMember));
        given(passwordEncoder.matches(request.getPassword(), testMember.getPassword())).willReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.MEMBER_INACTIVE));
    }

    @Test
    @DisplayName("getMe 호출 시 사용자 정보를 정상 반환한다")
    void getMe_정상() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));

        MemberResponse response = authService.getMe(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("hong_gildong");
    }

    @Test
    @DisplayName("getMe 호출 시 사용자가 없으면 MEMBER_NOT_FOUND 예외가 발생한다")
    void getMe_없는사용자_MEMBER_NOT_FOUND() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }
}
