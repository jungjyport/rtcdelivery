package com.rtcdelivery.memberauth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.memberauth.config.SecurityConfig;
import com.rtcdelivery.memberauth.domain.AuthProvider;
import com.rtcdelivery.memberauth.domain.Role;
import com.rtcdelivery.memberauth.dto.request.LoginRequest;
import com.rtcdelivery.memberauth.dto.request.SignupRequest;
import com.rtcdelivery.memberauth.dto.response.LoginResponse;
import com.rtcdelivery.memberauth.dto.response.MemberResponse;
import com.rtcdelivery.memberauth.dto.response.TokenResponse;
import com.rtcdelivery.memberauth.exception.GlobalExceptionHandler;
import com.rtcdelivery.memberauth.exception.RestAccessDeniedHandler;
import com.rtcdelivery.memberauth.exception.RestAuthenticationEntryPoint;
import com.rtcdelivery.memberauth.security.CookieProperties;
import com.rtcdelivery.memberauth.security.CookieUtils;
import com.rtcdelivery.memberauth.security.HeaderAuthenticationFilter;
import com.rtcdelivery.memberauth.service.AuthService;
import com.rtcdelivery.memberauth.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        HeaderAuthenticationFilter.class,
        CookieUtils.class,
        CookieProperties.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    private MemberResponse testMemberResponse;

    @BeforeEach
    void setUp() {
        testMemberResponse = MemberResponse.builder()
                .id(1L)
                .username("hong_gildong")
                .nickname("홍길동")
                .email("hong@example.com")
                .role(Role.ROLE_USER)
                .authProvider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("회원가입 검증 실패 시 400 VALIDATION_ERROR와 data 필드에 상세 정보가 반환된다")
    void signup_검증실패_400_VALIDATION_ERROR() throws Exception {
        SignupRequest invalidRequest = SignupRequest.builder()
                .username("ab") // 4자 미만
                .password("")   // 공백
                .nickname("")   // 공백
                .email("invalid-email")
                .build();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.username").exists())
                .andExpect(jsonPath("$.data.password").exists())
                .andExpect(jsonPath("$.data.nickname").exists())
                .andExpect(jsonPath("$.data.email").exists());
    }

    @Test
    @DisplayName("회원가입 정상 요청 시 201 Created와 생성된 회원 정보가 반환된다")
    void signup_성공_201_CREATED() throws Exception {
        SignupRequest request = SignupRequest.builder()
                .username("hong_gildong")
                .password("Passw0rd!")
                .nickname("홍길동")
                .email("hong@example.com")
                .build();

        given(authService.signup(any(SignupRequest.class))).willReturn(testMemberResponse);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Created"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("hong_gildong"));
    }

    @Test
    @DisplayName("로그인 성공 시 200 OK와 HttpOnly Refresh Token 쿠키 및 Access Token이 반환된다")
    void login_성공_SetCookie_HttpOnly포함() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("hong_gildong")
                .password("Passw0rd!")
                .build();

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("mock_access_token")
                .tokenType("Bearer")
                .expiresIn(1800)
                .user(testMemberResponse)
                .build();

        given(authService.login(any(LoginRequest.class)))
                .willReturn(new AuthService.AuthLoginResult(loginResponse, "mock_refresh_token"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=mock_refresh_token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("mock_access_token"))
                .andExpect(jsonPath("$.data.user.username").value("hong_gildong"));
    }

    @Test
    @DisplayName("쿠키 없이 토큰 갱신 요청 시 401 REFRESH_TOKEN_NOT_FOUND 및 쿠키 삭제 헤더가 반환된다")
    void refresh_쿠키없음_401_REFRESH_TOKEN_NOT_FOUND() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("REFRESH_TOKEN_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("유효한 쿠키로 갱신 시 200 OK와 회전된 새 RT 쿠키가 내려온다")
    void refresh_성공() throws Exception {
        Cookie rtCookie = new Cookie("refreshToken", "old_refresh_token");

        given(refreshTokenService.rotateRefreshToken("old_refresh_token"))
                .willReturn(new RefreshTokenService.TokenRotationResult("new_access_token", "new_refresh_token"));

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(rtCookie))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=new_refresh_token")))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("new_access_token"));
    }

    @Test
    @DisplayName("로그아웃 시 200 OK와 삭제 쿠키(Max-Age=0)가 반환된다")
    void logout_성공_200() throws Exception {
        Cookie rtCookie = new Cookie("refreshToken", "some_refresh_token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(rtCookie))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("인증 헤더 없이 /auth/me 호출 시 401 UNAUTHORIZED가 반환된다")
    void me_인증헤더없음_401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Gateway 주입 헤더(X-User-Id)와 함께 /auth/me 호출 시 200 OK와 내 정보가 반환된다")
    void me_XUserId헤더_200_OK() throws Exception {
        given(authService.getMe(1L)).willReturn(testMemberResponse);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("hong_gildong"));
    }
}
