package com.rtcdelivery.memberauth.controller;

import com.rtcdelivery.memberauth.common.ApiResponse;
import com.rtcdelivery.memberauth.dto.request.LoginRequest;
import com.rtcdelivery.memberauth.dto.request.SignupRequest;
import com.rtcdelivery.memberauth.dto.response.LoginResponse;
import com.rtcdelivery.memberauth.dto.response.MemberResponse;
import com.rtcdelivery.memberauth.dto.response.TokenResponse;
import com.rtcdelivery.memberauth.exception.BusinessException;
import com.rtcdelivery.memberauth.exception.ErrorCode;
import com.rtcdelivery.memberauth.security.CookieUtils;
import com.rtcdelivery.memberauth.service.AuthService;
import com.rtcdelivery.memberauth.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Auth", description = "인증/인가 및 회원 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtils cookieUtils;

    @Operation(summary = "회원가입", description = "새로운 회원을 등록합니다. 가입 완료 후 로그인을 통해 토큰을 발급받아야 합니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody SignupRequest request) {
        MemberResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "로그인", description = "아이디/비밀번호로 로그인하여 Access Token을 발급받고 Refresh Token 쿠키를 설정합니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthService.AuthLoginResult result = authService.login(request);
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieUtils.createRefreshTokenCookie(result.refreshToken()).toString()
        );
        return ResponseEntity.ok(ApiResponse.ok(result.loginResponse()));
    }

    @Operation(summary = "토큰 갱신", description = "Refresh Token 쿠키를 이용해 새 Access Token을 발급받고 Refresh Token을 회전(RTR)합니다.")
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {

        String oldRefreshToken = cookieUtils.extractRefreshToken(request)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        RefreshTokenService.TokenRotationResult result = refreshTokenService.rotateRefreshToken(oldRefreshToken);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieUtils.createRefreshTokenCookie(result.refreshToken()).toString()
        );
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse.of(result.accessToken())));
    }

    @Operation(summary = "로그아웃", description = "Refresh Token을 무효화하고 쿠키를 삭제합니다. 항상 200 OK를 반환합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        cookieUtils.extractRefreshToken(request)
                .ifPresent(refreshTokenService::invalidateRefreshToken);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieUtils.createDeleteRefreshTokenCookie().toString()
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "내 정보 조회", description = "인증된 사용자의 프로필 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMe(@AuthenticationPrincipal Long userId) {
        MemberResponse response = authService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
