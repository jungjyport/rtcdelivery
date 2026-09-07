package com.rtcdelivery.memberauth.security;

import com.rtcdelivery.memberauth.domain.Member;
import com.rtcdelivery.memberauth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final ResourceLoader resourceLoader;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            this.privateKey = loadPrivateKey();
            this.publicKey = loadPublicKey();
            log.info("JWT RSA Key Pair initialized successfully.");
        } catch (Exception e) {
            log.error("Failed to initialize RSA keys for JWT", e);
            throw new IllegalStateException("Failed to initialize RSA keys", e);
        }
    }

    public String createAccessToken(Member member) {
        return createAccessToken(member.getId(), member.getUsername(), member.getRole());
    }

    public String createAccessToken(Long userId, String username, Role role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenValidity());

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String createRefreshToken(Member member) {
        return createRefreshToken(member.getId(), member.getUsername());
    }

    public String createRefreshToken(Long userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getRefreshTokenValidity());

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getUserId(String token) {
        Object userId = parseClaims(token).get(CLAIM_USER_ID);
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return userId != null ? Long.parseLong(userId.toString()) : null;
    }

    public String getTokenType(String token) {
        return (String) parseClaims(token).get(CLAIM_TYPE);
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String keyContent = jwtProperties.getPrivateKey();
        if (keyContent == null || keyContent.isBlank()) {
            Resource resource = resourceLoader.getResource(jwtProperties.getPrivateKeyLocation());
            try (InputStream is = resource.getInputStream()) {
                keyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        String sanitized = keyContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(sanitized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PublicKey loadPublicKey() throws Exception {
        String keyContent = jwtProperties.getPublicKey();
        if (keyContent == null || keyContent.isBlank()) {
            Resource resource = resourceLoader.getResource(jwtProperties.getPublicKeyLocation());
            try (InputStream is = resource.getInputStream()) {
                keyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        String sanitized = keyContent
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(sanitized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
