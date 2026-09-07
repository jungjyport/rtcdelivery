package com.rtcdelivery.memberauth.security;

import com.rtcdelivery.memberauth.domain.AuthProvider;
import com.rtcdelivery.memberauth.domain.Member;
import com.rtcdelivery.memberauth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() throws Exception {
        jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("rtc-delivery");
        jwtProperties.setAccessTokenValidity(1800000L);
        jwtProperties.setRefreshTokenValidity(604800000L);
        jwtProperties.setPrivateKeyLocation("classpath:keys/jwt-private.pem");
        jwtProperties.setPublicKeyLocation("classpath:keys/jwt-public.pem");

        jwtProvider = new JwtProvider(jwtProperties, new DefaultResourceLoader());
        jwtProvider.init();
    }

    private Member createTestMember() {
        return Member.builder()
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
    @DisplayName("Access Token 생성 시 필수 클레임(sub, userId, role, typ=access)이 포함된다")
    void createAccessToken_클레임포함() {
        Member member = createTestMember();

        String token = jwtProvider.createAccessToken(member);

        assertThat(token).isNotBlank();
        Claims claims = jwtProvider.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("hong_gildong");
        assertThat(jwtProvider.getUserId(token)).isEqualTo(1L);
        assertThat(claims.get(JwtProvider.CLAIM_ROLE)).isEqualTo("ROLE_USER");
        assertThat(claims.get(JwtProvider.CLAIM_TYPE)).isEqualTo(JwtProvider.TYPE_ACCESS);
        assertThat(claims.getIssuer()).isEqualTo("rtc-delivery");
    }

    @Test
    @DisplayName("Refresh Token에는 role 클레임이 없고 typ=refresh가 포함된다")
    void createRefreshToken_role클레임없음() {
        Member member = createTestMember();

        String token = jwtProvider.createRefreshToken(member);

        assertThat(token).isNotBlank();
        Claims claims = jwtProvider.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("hong_gildong");
        assertThat(jwtProvider.getUserId(token)).isEqualTo(1L);
        assertThat(claims.get(JwtProvider.CLAIM_ROLE)).isNull();
        assertThat(claims.get(JwtProvider.CLAIM_TYPE)).isEqualTo(JwtProvider.TYPE_REFRESH);
        assertThat(claims.getIssuer()).isEqualTo("rtc-delivery");
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰 파싱 시 JwtException이 발생한다")
    void parse_다른키로서명된토큰_검증실패() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherPair = generator.generateKeyPair();

        String otherToken = Jwts.builder()
                .issuer("rtc-delivery")
                .subject("attacker")
                .claim(JwtProvider.CLAIM_USER_ID, 999L)
                .claim(JwtProvider.CLAIM_TYPE, JwtProvider.TYPE_ACCESS)
                .signWith(otherPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> jwtProvider.parseClaims(otherToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료된 토큰 파싱 시 ExpiredJwtException이 발생한다")
    void parse_만료토큰_ExpiredJwtException() throws Exception {
        jwtProperties.setAccessTokenValidity(-1000L); // 이미 만료
        JwtProvider expiredJwtProvider = new JwtProvider(jwtProperties, new DefaultResourceLoader());
        expiredJwtProvider.init();

        Member member = createTestMember();
        String expiredToken = expiredJwtProvider.createAccessToken(member);

        assertThatThrownBy(() -> jwtProvider.parseClaims(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
