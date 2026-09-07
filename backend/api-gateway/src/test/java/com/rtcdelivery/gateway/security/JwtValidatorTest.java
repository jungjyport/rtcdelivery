package com.rtcdelivery.gateway.security;

import com.rtcdelivery.gateway.config.JwtProperties;
import com.rtcdelivery.gateway.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    private JwtValidator jwtValidator;
    private JwtProperties jwtProperties;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("rtc-delivery");
        jwtProperties.setPublicKeyLocation("classpath:keys/jwt-public.pem");

        jwtValidator = new JwtValidator(jwtProperties, new DefaultResourceLoader());
        jwtValidator.init();

        // 테스트용 서명에 사용할 개인키 생성 또는 파일 로드
        // 실제 classpath:keys/jwt-public.pem과 쌍을 이루는 테스트 토큰 생성을 위해
        // 동일한 키 쌍 혹은 커스텀 공개키 주입 방식 사용
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        // 커스텀 키 쌍으로 재설정
        Base64.Encoder encoder = Base64.getMimeEncoder(64, new byte[]{'\n'});
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
                + encoder.encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";

        jwtProperties.setPublicKey(publicKeyPem);
        jwtValidator = new JwtValidator(jwtProperties, new DefaultResourceLoader());
        jwtValidator.init();
    }

    private String createAccessToken(String sub, Long userId, String role, long validityMs, PrivateKey privateKey, String issuer, String typ) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + validityMs);

        return Jwts.builder()
                .issuer(issuer)
                .subject(sub)
                .claim(JwtValidator.CLAIM_USER_ID, userId)
                .claim(JwtValidator.CLAIM_ROLE, role)
                .claim(JwtValidator.CLAIM_TYPE, typ)
                .issuedAt(now)
                .expiration(exp)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    @Test
    @DisplayName("유효한 Access Token 검증 시 성공하고 클레임(userId, sub, role)을 반환한다")
    void validate_유효한AccessToken_클레임반환() {
        String token = createAccessToken("hong_gildong", 1L, "ROLE_USER", 1800000L, keyPair.getPrivate(), "rtc-delivery", "access");

        JwtValidator.ValidationResult result = jwtValidator.validate(token);

        assertThat(result.isValid()).isTrue();
        assertThat(result.claims()).isNotNull();
        assertThat(result.claims().getSubject()).isEqualTo("hong_gildong");
        assertThat(result.claims().get(JwtValidator.CLAIM_USER_ID, Long.class)).isEqualTo(1L);
        assertThat(result.claims().get(JwtValidator.CLAIM_ROLE, String.class)).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("만료된 Access Token 검증 시 실패하고 ACCESS_TOKEN_EXPIRED를 반환한다")
    void validate_만료토큰_ACCESS_TOKEN_EXPIRED() {
        String token = createAccessToken("hong_gildong", 1L, "ROLE_USER", -1000L, keyPair.getPrivate(), "rtc-delivery", "access");

        JwtValidator.ValidationResult result = jwtValidator.validate(token);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.ACCESS_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("다른 개인키로 서명된 토큰 검증 시 실패하고 ACCESS_TOKEN_INVALID를 반환한다")
    void validate_다른키로서명_ACCESS_TOKEN_INVALID() throws Exception {
        KeyPairGenerator otherGen = KeyPairGenerator.getInstance("RSA");
        otherGen.initialize(2048);
        KeyPair otherPair = otherGen.generateKeyPair();

        String token = createAccessToken("hong_gildong", 1L, "ROLE_USER", 1800000L, otherPair.getPrivate(), "rtc-delivery", "access");

        JwtValidator.ValidationResult result = jwtValidator.validate(token);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID);
    }

    @Test
    @DisplayName("Refresh Token(typ=refresh)을 전달할 경우 ACCESS_TOKEN_INVALID를 반환한다")
    void validate_RefreshToken전달_ACCESS_TOKEN_INVALID() {
        String token = createAccessToken("hong_gildong", 1L, "ROLE_USER", 1800000L, keyPair.getPrivate(), "rtc-delivery", "refresh");

        JwtValidator.ValidationResult result = jwtValidator.validate(token);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID);
    }

    @Test
    @DisplayName("발급자(iss)가 일치하지 않는 경우 ACCESS_TOKEN_INVALID를 반환한다")
    void validate_iss불일치_ACCESS_TOKEN_INVALID() {
        String token = createAccessToken("hong_gildong", 1L, "ROLE_USER", 1800000L, keyPair.getPrivate(), "wrong-issuer", "access");

        JwtValidator.ValidationResult result = jwtValidator.validate(token);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID);
    }
}
