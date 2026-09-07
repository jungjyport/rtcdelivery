package com.rtcdelivery.gateway.security;

import com.rtcdelivery.gateway.config.JwtProperties;
import com.rtcdelivery.gateway.exception.ErrorCode;
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
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtValidator {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";

    private final JwtProperties jwtProperties;
    private final ResourceLoader resourceLoader;

    private PublicKey publicKey;

    public record ValidationResult(boolean isValid, Claims claims, ErrorCode errorCode) {
        public static ValidationResult success(Claims claims) {
            return new ValidationResult(true, claims, null);
        }

        public static ValidationResult failure(ErrorCode errorCode) {
            return new ValidationResult(false, null, errorCode);
        }
    }

    @PostConstruct
    public void init() {
        try {
            this.publicKey = loadPublicKey();
            log.info("Gateway JwtValidator initialized with public key.");
        } catch (Exception e) {
            log.error("Failed to initialize public key for Gateway JWT verification", e);
            throw new IllegalStateException("Failed to load JWT public key", e);
        }
    }

    public ValidationResult validate(String token) {
        if (token == null || token.isBlank()) {
            return ValidationResult.failure(ErrorCode.ACCESS_TOKEN_INVALID);
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Access token is expired");
            return ValidationResult.failure(ErrorCode.ACCESS_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return ValidationResult.failure(ErrorCode.ACCESS_TOKEN_INVALID);
        }

        // 1. 발급자 (iss) 검증
        if (!jwtProperties.getIssuer().equals(claims.getIssuer())) {
            log.debug("Issuer mismatch: expected={}, actual={}", jwtProperties.getIssuer(), claims.getIssuer());
            return ValidationResult.failure(ErrorCode.ACCESS_TOKEN_INVALID);
        }

        // 2. typ == "access" 검증
        String typ = (String) claims.get(CLAIM_TYPE);
        if (!TYPE_ACCESS.equals(typ)) {
            log.debug("Token type is not access: actual={}", typ);
            return ValidationResult.failure(ErrorCode.ACCESS_TOKEN_INVALID);
        }

        return ValidationResult.success(claims);
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
