package com.rtcdelivery.memberauth.service;

import com.rtcdelivery.memberauth.domain.Member;
import com.rtcdelivery.memberauth.exception.BusinessException;
import com.rtcdelivery.memberauth.exception.ErrorCode;
import com.rtcdelivery.memberauth.repository.MemberRepository;
import com.rtcdelivery.memberauth.security.JwtProperties;
import com.rtcdelivery.memberauth.security.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REDIS_PREFIX = "RT:";

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final MemberRepository memberRepository;

    public record TokenRotationResult(String accessToken, String refreshToken) {}

    public void saveRefreshToken(String username, String refreshToken) {
        String key = REDIS_PREFIX + username;
        redisTemplate.opsForValue().set(
                key,
                refreshToken,
                Duration.ofMillis(jwtProperties.getRefreshTokenValidity())
        );
    }

    public TokenRotationResult rotateRefreshToken(String oldRefreshToken) {
        if (oldRefreshToken == null || oldRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        Claims claims;
        try {
            claims = jwtProvider.parseClaims(oldRefreshToken);
        } catch (ExpiredJwtException e) {
            log.warn("Refresh token is expired");
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid refresh token signature or structure", e);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String type = (String) claims.get(JwtProvider.CLAIM_TYPE);
        if (!JwtProvider.TYPE_REFRESH.equals(type)) {
            log.warn("Token type is not refresh: {}", type);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String username = claims.getSubject();
        String key = REDIS_PREFIX + username;
        String storedToken = redisTemplate.opsForValue().get(key);

        if (storedToken == null) {
            log.warn("No stored refresh token found for user: {}", username);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 재사용 탐지: 저장된 토큰과 요청 토큰이 불일치하면 세션 전체 무효화
        if (!storedToken.equals(oldRefreshToken)) {
            log.error("Refresh token reuse detected for user: {}. Invalidating session.", username);
            redisTemplate.delete(key);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Member member = memberRepository.findByUsername(username)
                .orElse(null);

        if (member == null || !member.isActive()) {
            log.warn("Member not found or inactive during token rotation: {}", username);
            redisTemplate.delete(key);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 6. 새 AT + 새 RT 발급
        String newAccessToken = jwtProvider.createAccessToken(member);
        String newRefreshToken = jwtProvider.createRefreshToken(member);

        // 7. SET RT:{username} = 새 RT (TTL 7일, 덮어쓰기)
        saveRefreshToken(username, newRefreshToken);

        return new TokenRotationResult(newAccessToken, newRefreshToken);
    }

    /**
     * 사용자명으로 저장된 Refresh Token을 즉시 폐기한다. 역할 변경·계정 비활성화처럼
     * 토큰을 손에 쥐고 있지 않은 상태에서 세션을 끊어야 할 때 사용한다.
     */
    public void invalidateByUsername(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        redisTemplate.delete(REDIS_PREFIX + username);
        log.info("Invalidated refresh token by username: {}", username);
    }

    public void invalidateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            Claims claims = jwtProvider.parseClaims(refreshToken);
            String username = claims.getSubject();
            if (username != null) {
                redisTemplate.delete(REDIS_PREFIX + username);
                log.info("Invalidated refresh token for user: {}", username);
            }
        } catch (Exception e) {
            log.debug("Failed to parse refresh token on logout, ignoring: {}", e.getMessage());
        }
    }
}
