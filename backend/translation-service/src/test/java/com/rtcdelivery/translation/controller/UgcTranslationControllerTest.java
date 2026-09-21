package com.rtcdelivery.translation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.translation.config.LocaleConfig;
import com.rtcdelivery.translation.config.SecurityConfig;
import com.rtcdelivery.translation.domain.UgcContentType;
import com.rtcdelivery.translation.dto.request.UgcTranslationRequest;
import com.rtcdelivery.translation.dto.response.UgcTranslationResponse;
import com.rtcdelivery.translation.exception.ErrorResponseWriter;
import com.rtcdelivery.translation.exception.RestAccessDeniedHandler;
import com.rtcdelivery.translation.exception.RestAuthenticationEntryPoint;
import com.rtcdelivery.translation.security.HeaderAuthenticationFilter;
import com.rtcdelivery.translation.service.UgcTranslationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UgcTranslationController.class)
@Import({
        SecurityConfig.class,
        HeaderAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ErrorResponseWriter.class,
        LocaleConfig.class
})
class UgcTranslationControllerTest {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UgcTranslationService ugcTranslationService;

    @Test
    @DisplayName("비로그인 사용자가 요청하면 401 UNAUTHORIZED 를 반환한다")
    void translateUgc_unauthorized_returns401() throws Exception {
        UgcTranslationRequest request = UgcTranslationRequest.builder()
                .contentType(UgcContentType.REVIEW)
                .contentId(1L)
                .text("정말 맛있어요")
                .sourceLocale("ko")
                .build();

        mockMvc.perform(post("/api/v1/translations/ugc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("로그인 사용자의 정상 요청 시 200과 번역 결과를 반환한다")
    void translateUgc_authenticated_returns200() throws Exception {
        UgcTranslationRequest request = UgcTranslationRequest.builder()
                .contentType(UgcContentType.REVIEW)
                .contentId(1L)
                .text("정말 맛있어요")
                .sourceLocale("ko")
                .build();

        UgcTranslationResponse response = UgcTranslationResponse.builder()
                .contentType(UgcContentType.REVIEW)
                .contentId(1L)
                .sourceLocale("ko")
                .targetLocale("ja")
                .translatedText("とても美味しいです")
                .cached(false)
                .build();

        given(ugcTranslationService.translateUgc(any(), eq("ja"), any()))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/translations/ugc")
                        .header(HEADER_USER_ID, "100")
                        .header(HEADER_USER_ROLE, "ROLE_USER")
                        .header("Accept-Language", "ja")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.translatedText").value("とても美味しいです"))
                .andExpect(jsonPath("$.data.targetLocale").value("ja"));
    }

    @Test
    @DisplayName("원문 텍스트가 2000자를 초과하면 400 VALIDATION_ERROR 를 반환한다")
    void translateUgc_textTooLong_returns400() throws Exception {
        String longText = "a".repeat(2001);
        UgcTranslationRequest request = UgcTranslationRequest.builder()
                .contentType(UgcContentType.REVIEW)
                .contentId(1L)
                .text(longText)
                .sourceLocale("ko")
                .build();

        mockMvc.perform(post("/api/v1/translations/ugc")
                        .header(HEADER_USER_ID, "100")
                        .header(HEADER_USER_ROLE, "ROLE_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("VALIDATION_ERROR"));
    }
}
