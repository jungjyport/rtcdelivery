package com.rtcdelivery.foodcatalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.foodcatalog.config.LocaleConfig;
import com.rtcdelivery.foodcatalog.config.SecurityConfig;
import com.rtcdelivery.foodcatalog.dto.request.RestaurantCreateRequest;
import com.rtcdelivery.foodcatalog.dto.response.PageResponse;
import com.rtcdelivery.foodcatalog.dto.response.RestaurantResponse;
import com.rtcdelivery.foodcatalog.exception.BusinessException;
import com.rtcdelivery.foodcatalog.exception.ErrorCode;
import com.rtcdelivery.foodcatalog.exception.RestAccessDeniedHandler;
import com.rtcdelivery.foodcatalog.exception.RestAuthenticationEntryPoint;
import com.rtcdelivery.foodcatalog.security.Actor;
import com.rtcdelivery.foodcatalog.security.HeaderAuthenticationFilter;
import com.rtcdelivery.foodcatalog.service.FoodService;
import com.rtcdelivery.foodcatalog.service.RestaurantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gateway 헤더 기반 인가의 배선을 검증한다. 특히 비로그인(401)과 역할 불일치(403)가
 * 구분되는지 확인한다. 필터 레벨에서 {@code authenticated()}를 먼저 걸지 않으면 둘 다 403이 된다.
 */
@WebMvcTest(controllers = RestaurantController.class)
@Import({
        SecurityConfig.class,
        HeaderAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        LocaleConfig.class
})
class RestaurantControllerTest {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RestaurantService restaurantService;

    @MockitoBean
    private FoodService foodService;

    private static RestaurantCreateRequest validCreateRequest() {
        return RestaurantCreateRequest.builder()
                .categoryId(1L)
                .name("서울 김치찌개")
                .address("서울시 강남구")
                .build();
    }

    @Test
    @DisplayName("목록_조회는_비로그인_사용자에게도_열려_있다")
    void search_anonymous_isAllowed() throws Exception {
        given(restaurantService.search(any(), any(), any(), eq("ko")))
                .willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("Accept-Language_헤더의_locale이_서비스로_전달된다")
    void search_acceptLanguageJa_passesJaLocale() throws Exception {
        given(restaurantService.search(any(), any(), any(), eq("ja")))
                .willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/restaurants").header("Accept-Language", "ja-JP,ja;q=0.9,en;q=0.8"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("지원하지_않는_Accept-Language는_ko로_폴백한다")
    void search_unsupportedAcceptLanguage_fallsBackToKo() throws Exception {
        given(restaurantService.search(any(), any(), any(), eq("ko")))
                .willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/restaurants").header("Accept-Language", "fr-FR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("등록_비로그인이면_403이_아니라_401을_반환한다")
    void create_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    @DisplayName("등록_일반_사용자_역할이면_403을_반환한다")
    void create_userRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/restaurants")
                        .header(HEADER_USER_ID, "100")
                        .header(HEADER_USER_ROLE, "ROLE_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.code()));
    }

    @Test
    @DisplayName("등록_점주_역할이면_201을_반환한다")
    void create_ownerRole_returns201() throws Exception {
        given(restaurantService.create(any(RestaurantCreateRequest.class), any(Actor.class), eq("ko")))
                .willReturn(new RestaurantResponse(
                        1L, 1L, "korean", "서울 김치찌개", null,
                        "서울시 강남구", null, 0, 0, null, "ko"));

        mockMvc.perform(post("/api/v1/restaurants")
                        .header(HEADER_USER_ID, "100")
                        .header(HEADER_USER_ROLE, "ROLE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.name").value("서울 김치찌개"));
    }

    @Test
    @DisplayName("등록_필수값이_빠지면_400과_필드별_상세를_반환한다")
    void create_missingRequiredField_returns400WithDetails() throws Exception {
        RestaurantCreateRequest invalid = RestaurantCreateRequest.builder().categoryId(1L).build();

        mockMvc.perform(post("/api/v1/restaurants")
                        .header(HEADER_USER_ID, "100")
                        .header(HEADER_USER_ROLE, "ROLE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.VALIDATION_ERROR.code()))
                .andExpect(jsonPath("$.data.name").exists());
    }

    /**
     * 에러 응답에서도 {@code data} 키가 살아 있어야 한다. 프론트 API Client가 이 키의 존재로
     * {@code ApiResponse} 여부를 판정하므로, 빠지면 에러 코드 파싱이 통째로 깨진다.
     */
    @Test
    @DisplayName("상세_조회_없는_음식점은_404와_에러_코드를_반환하고_data_키를_유지한다")
    void getDetail_unknownId_returns404WithDataKey() throws Exception {
        willThrow(new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND))
                .given(restaurantService).getDetail(eq(99L), any());

        mockMvc.perform(get("/api/v1/restaurants/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ErrorCode.RESTAURANT_NOT_FOUND.code()))
                .andExpect(content().string(containsString("\"data\":null")));
    }
}
