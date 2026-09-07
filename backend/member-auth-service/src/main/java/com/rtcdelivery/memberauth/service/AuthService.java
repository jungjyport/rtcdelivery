package com.rtcdelivery.memberauth.service;

import com.rtcdelivery.memberauth.domain.AuthProvider;
import com.rtcdelivery.memberauth.domain.Member;
import com.rtcdelivery.memberauth.domain.Role;
import com.rtcdelivery.memberauth.dto.request.LoginRequest;
import com.rtcdelivery.memberauth.dto.request.SignupRequest;
import com.rtcdelivery.memberauth.dto.response.LoginResponse;
import com.rtcdelivery.memberauth.dto.response.MemberResponse;
import com.rtcdelivery.memberauth.exception.BusinessException;
import com.rtcdelivery.memberauth.exception.ErrorCode;
import com.rtcdelivery.memberauth.repository.MemberRepository;
import com.rtcdelivery.memberauth.security.JwtProperties;
import com.rtcdelivery.memberauth.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // 타이밍 공격 완화를 위한 더미 해시 (BCrypt)
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5VpI5zOa2Ibgk1p6xZ9d2jWw8i4ye";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;

    public record AuthLoginResult(LoginResponse loginResponse, String refreshToken) {}

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        if (memberRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (memberRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
        }

        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        Member member = Member.builder()
                .username(request.getUsername())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .email((request.getEmail() != null && !request.getEmail().isBlank()) ? request.getEmail() : null)
                .role(Role.ROLE_USER)
                .authProvider(AuthProvider.LOCAL)
                .isActive(true)
                .build();

        try {
            Member savedMember = memberRepository.save(member);
            return MemberResponse.from(savedMember);
        } catch (DataIntegrityViolationException e) {
            log.warn("Data integrity violation during signup: {}", e.getMessage());
            // 동시 요청 경합으로 인한 유니크 제약 위반 재확인
            if (memberRepository.existsByUsername(request.getUsername())) {
                throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
            }
            if (request.getEmail() != null && memberRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            if (memberRepository.existsByNickname(request.getNickname())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_VIOLATION);
        }
    }

    @Transactional
    public AuthLoginResult login(LoginRequest request) {
        Member member = memberRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (member == null) {
            // 타이밍 공격 완화
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (member.getPassword() == null || !passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!member.isActive()) {
            throw new BusinessException(ErrorCode.MEMBER_INACTIVE);
        }

        String accessToken = jwtProvider.createAccessToken(member);
        String refreshToken = jwtProvider.createRefreshToken(member);

        refreshTokenService.saveRefreshToken(member.getUsername(), refreshToken);

        long expiresInSeconds = jwtProperties.getAccessTokenValidity() / 1000;
        LoginResponse loginResponse = LoginResponse.of(accessToken, expiresInSeconds, member);

        return new AuthLoginResult(loginResponse, refreshToken);
    }

    @Transactional(readOnly = true)
    public MemberResponse getMe(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        return MemberResponse.from(member);
    }
}
