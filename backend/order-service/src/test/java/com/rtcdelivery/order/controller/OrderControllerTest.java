package com.rtcdelivery.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.order.config.SecurityConfig;
import com.rtcdelivery.order.domain.FulfillmentStatus;
import com.rtcdelivery.order.domain.PaymentStatus;
import com.rtcdelivery.order.dto.request.OrderCreateRequest;
import com.rtcdelivery.order.dto.request.OrderStatusUpdateRequest;
import com.rtcdelivery.order.dto.response.OrderResponse;
import com.rtcdelivery.order.dto.response.PageResponse;
import com.rtcdelivery.order.exception.RestAccessDeniedHandler;
import com.rtcdelivery.order.exception.RestAuthenticationEntryPoint;
import com.rtcdelivery.order.security.HeaderAuthenticationFilter;
import com.rtcdelivery.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({
        SecurityConfig.class,
        HeaderAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class OrderControllerTest {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private OrderResponse sampleOrderResponse() {
        return new OrderResponse(
                100L, 1L, "서울 김치찌개", FulfillmentStatus.PENDING, PaymentStatus.UNPAID,
                9000, 3000, 12000, "홍길동", "010-1234-5678", "서울시 강남구", null,
                List.of(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("createOrder_비로그인_요청은_401_UNAUTHORIZED")
    void createOrder_unauthenticated_returns401() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                1L, List.of(new OrderCreateRequest.OrderItemRequest(10L, 1)),
                "홍길동", "010-1234-5678", "서울시 강남구", null
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("createOrder_인증된_사용자의_신규_주문_생성은_201_CREATED")
    void createOrder_newOrder_returns201() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                1L, List.of(new OrderCreateRequest.OrderItemRequest(10L, 1)),
                "홍길동", "010-1234-5678", "서울시 강남구", null
        );

        given(orderService.createOrder(any(), any(), any()))
                .willReturn(new OrderService.CreateOrderResult(sampleOrderResponse(), true));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HEADER_USER_ID, "10")
                        .header(HEADER_USER_ROLE, "ROLE_USER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("createOrder_동일_키_재전송은_200_OK로_반환")
    void createOrder_idempotentResend_returns200() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                1L, List.of(new OrderCreateRequest.OrderItemRequest(10L, 1)),
                "홍길동", "010-1234-5678", "서울시 강남구", null
        );

        given(orderService.createOrder(any(), any(), any()))
                .willReturn(new OrderService.CreateOrderResult(sampleOrderResponse(), false));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HEADER_USER_ID, "10")
                        .header(HEADER_USER_ROLE, "ROLE_USER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("getMyOrders_내 주문 목록 조회 성공 200 OK")
    void getMyOrders_success() throws Exception {
        given(orderService.getMyOrders(any(), any()))
                .willReturn(new PageResponse<>(List.of(sampleOrderResponse()), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/orders")
                        .header(HEADER_USER_ID, "10")
                        .header(HEADER_USER_ROLE, "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(100));
    }

    @Test
    @DisplayName("getOrder_주문 단건 조회 성공 200 OK")
    void getOrder_success() throws Exception {
        given(orderService.getOrderDetail(eq(100L), any()))
                .willReturn(sampleOrderResponse());

        mockMvc.perform(get("/api/v1/orders/100")
                        .header(HEADER_USER_ID, "10")
                        .header(HEADER_USER_ROLE, "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("getManagedOrders_일반_회원은_403_FORBIDDEN")
    void getManagedOrders_byUser_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/orders/managed")
                        .header(HEADER_USER_ID, "10")
                        .header(HEADER_USER_ROLE, "ROLE_USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getManagedOrders_점주는_200_OK")
    void getManagedOrders_byOwner_returns200() throws Exception {
        given(orderService.getManagedOrders(any(), any(), any(), any()))
                .willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/orders/managed")
                        .header(HEADER_USER_ID, "20")
                        .header(HEADER_USER_ROLE, "ROLE_OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("updateFulfillmentStatus_상태 변경 성공 200 OK")
    void updateFulfillmentStatus_success() throws Exception {
        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(FulfillmentStatus.PREPARING);
        given(orderService.updateFulfillmentStatus(eq(100L), eq(FulfillmentStatus.PREPARING), any()))
                .willReturn(sampleOrderResponse());

        mockMvc.perform(patch("/api/v1/orders/100/status")
                        .header(HEADER_USER_ID, "20")
                        .header(HEADER_USER_ROLE, "ROLE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("cancelOrder_주문 취소 성공 200 OK")
    void cancelOrder_success() throws Exception {
        given(orderService.cancelOrder(eq(100L), any()))
                .willReturn(sampleOrderResponse());

        mockMvc.perform(post("/api/v1/orders/100/cancel")
                        .header(HEADER_USER_ID, "10")
                        .header(HEADER_USER_ROLE, "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("requestRefund_관리자가_아니면_403_FORBIDDEN")
    void requestRefund_byNonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/orders/1/refund")
                        .header(HEADER_USER_ID, "20")
                        .header(HEADER_USER_ROLE, "ROLE_OWNER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("requestRefund_관리자는_200_OK")
    void requestRefund_byAdmin_returns200() throws Exception {
        given(orderService.requestRefund(eq(100L), any()))
                .willReturn(sampleOrderResponse());

        mockMvc.perform(post("/api/v1/orders/100/refund")
                        .header(HEADER_USER_ID, "1")
                        .header(HEADER_USER_ROLE, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }
}
