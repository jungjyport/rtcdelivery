package com.rtcdelivery.memberauth.service;

import com.rtcdelivery.memberauth.domain.AuthProvider;
import com.rtcdelivery.memberauth.domain.Member;
import com.rtcdelivery.memberauth.domain.Role;
import com.rtcdelivery.memberauth.exception.BusinessException;
import com.rtcdelivery.memberauth.exception.ErrorCode;
import com.rtcdelivery.memberauth.repository.MemberRepository;
import com.rtcdelivery.memberauth.security.JwtProperties;
import com.rtcdelivery.memberauth.security.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

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
    @DisplayName("유효한 Refresh Token으로 갱신 시 새 토큰 쌍이 발급되고 Redis에 갱신된다")
    void refresh_유효한토큰_새토큰발급및회전() {
        String oldToken = "valid_old_refresh_token";
        Claims claims = mock(Claims.class);
        given(claims.get(JwtProvider.CLAIM_TYPE)).willReturn(JwtProvider.TYPE_REFRESH);
        given(claims.getSubject()).willReturn("hong_gildong");
        given(jwtProvider.parseClaims(oldToken)).willReturn(claims);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:hong_gildong")).willReturn(oldToken);
        given(memberRepository.findByUsername("hong_gildong")).willReturn(Optional.of(testMember));

        given(jwtProvider.createAccessToken(testMember)).willReturn("new_access_token");
        given(jwtProvider.createRefreshToken(testMember)).willReturn("new_refresh_token");
        given(jwtProperties.getRefreshTokenValidity()).willReturn(604800000L);

        RefreshTokenService.TokenRotationResult result = refreshTokenService.rotateRefreshToken(oldToken);

        assertThat(result.accessToken()).isEqualTo("new_access_token");
        assertThat(result.refreshToken()).isEqualTo("new_refresh_token");

        verify(valueOperations).set(eq("RT:hong_gildong"), eq("new_refresh_token"), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 저장값과 다른 Refresh Token 요청 시 재사용으로 간주하여 세션이 무효화된다")
    void refresh_저장값과불일치_세션전체무효화() {
        String oldToken = "reused_refresh_token";
        Claims claims = mock(Claims.class);
        given(claims.get(JwtProvider.CLAIM_TYPE)).willReturn(JwtProvider.TYPE_REFRESH);
        given(claims.getSubject()).willReturn("hong_gildong");
        given(jwtProvider.parseClaims(oldToken)).willReturn(claims);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:hong_gildong")).willReturn("different_latest_token");

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(oldToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));

        verify(redisTemplate).delete("RT:hong_gildong");
    }

    @Test
    @DisplayName("만료된 Refresh Token 전달 시 REFRESH_TOKEN_EXPIRED 예외가 발생한다")
    void refresh_만료토큰_REFRESH_TOKEN_EXPIRED() {
        String expiredToken = "expired_token";
        given(jwtProvider.parseClaims(expiredToken)).willThrow(new ExpiredJwtException(null, null, "expired"));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(expiredToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("Redis에 저장된 토큰이 없는 경우 REFRESH_TOKEN_INVALID 예외가 발생한다")
    void refresh_Redis에없음_REFRESH_TOKEN_INVALID() {
        String token = "valid_signature_but_not_in_redis";
        Claims claims = mock(Claims.class);
        given(claims.get(JwtProvider.CLAIM_TYPE)).willReturn(JwtProvider.TYPE_REFRESH);
        given(claims.getSubject()).willReturn("hong_gildong");
        given(jwtProvider.parseClaims(token)).willReturn(claims);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:hong_gildong")).willReturn(null);

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(token))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("typ이 access인 토큰을 갱신 엔드포인트에 전달 시 REFRESH_TOKEN_INVALID 예외가 발생한다")
    void refresh_accessToken전달_REFRESH_TOKEN_INVALID() {
        String accessToken = "access_token_passed_as_refresh";
        Claims claims = mock(Claims.class);
        given(claims.get(JwtProvider.CLAIM_TYPE)).willReturn(JwtProvider.TYPE_ACCESS);
        given(jwtProvider.parseClaims(accessToken)).willReturn(claims);

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(accessToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("로그아웃 시 정상 토큰이면 Redis에서 삭제된다")
    void logout_정상토큰() {
        String token = "valid_refresh_token";
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn("hong_gildong");
        given(jwtProvider.parseClaims(token)).willReturn(claims);

        refreshTokenService.invalidateRefreshToken(token);

        verify(redisTemplate).delete("RT:hong_gildong");
    }

    @Test
    @DisplayName("로그아웃 시 만료/무효 토큰이어도 예외 없이 안전하게 무시된다")
    void logout_만료토큰_예외없이_안전처리() {
        String expiredToken = "expired_token";
        given(jwtProvider.parseClaims(expiredToken)).willThrow(new ExpiredJwtException(null, null, "expired"));

        // No exception thrown
        refreshTokenService.invalidateRefreshToken(expiredToken);
    }
}
