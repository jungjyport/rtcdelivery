package com.rtcdelivery.foodcatalog.controller;

import com.rtcdelivery.foodcatalog.config.LocaleConfig;
import com.rtcdelivery.foodcatalog.config.SecurityConfig;
import com.rtcdelivery.foodcatalog.dto.response.OrderSnapshotResponse;
import com.rtcdelivery.foodcatalog.exception.BusinessException;
import com.rtcdelivery.foodcatalog.exception.ErrorCode;
import com.rtcdelivery.foodcatalog.exception.RestAccessDeniedHandler;
import com.rtcdelivery.foodcatalog.exception.RestAuthenticationEntryPoint;
import com.rtcdelivery.foodcatalog.security.HeaderAuthenticationFilter;
import com.rtcdelivery.foodcatalog.service.RestaurantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalRestaurantController.class)
@Import({
        SecurityConfig.class,
        HeaderAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        LocaleConfig.class
})
class InternalRestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantService restaurantService;

    @Test
    @DisplayName("getOrderSnapshot_인증_헤더_없이도_200으로_성공한다")
    void getOrderSnapshot_withoutAuth_returns200() throws Exception {
        OrderSnapshotResponse snapshot = new OrderSnapshotResponse(
                1L,
                10L,
                true,
                "서울 김치찌개",
                3000,
                12000,
                List.of(new OrderSnapshotResponse.FoodSnapshotResponse(10L, "김치찌개", 9000, false))
        );

        given(restaurantService.getOrderSnapshot(1L)).willReturn(snapshot);

        mockMvc.perform(get("/internal/restaurants/1/order-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.restaurantId").value(1))
                .andExpect(jsonPath("$.data.ownerId").value(10))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.name").value("서울 김치찌개"))
                .andExpect(jsonPath("$.data.deliveryFee").value(3000))
                .andExpect(jsonPath("$.data.minOrderAmount").value(12000))
                .andExpect(jsonPath("$.data.foods[0].foodId").value(10))
                .andExpect(jsonPath("$.data.foods[0].name").value("김치찌개"))
                .andExpect(jsonPath("$.data.foods[0].price").value(9000))
                .andExpect(jsonPath("$.data.foods[0].soldOut").value(false));
    }

    @Test
    @DisplayName("getOrderSnapshot_없는_음식점이면_404_RESTAURANT_NOT_FOUND")
    void getOrderSnapshot_notFound_returns404() throws Exception {
        given(restaurantService.getOrderSnapshot(999L))
                .willThrow(new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        mockMvc.perform(get("/internal/restaurants/999/order-snapshot"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("RESTAURANT_NOT_FOUND"))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
