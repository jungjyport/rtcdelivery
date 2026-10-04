package com.rtcdelivery.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.payment.config.SecurityConfig;
import com.rtcdelivery.payment.domain.PaymentStatus;
import com.rtcdelivery.payment.dto.request.PaymentApproveRequest;
import com.rtcdelivery.payment.dto.response.PaymentResponse;
import com.rtcdelivery.payment.exception.RestAccessDeniedHandler;
import com.rtcdelivery.payment.exception.RestAuthenticationEntryPoint;
import com.rtcdelivery.payment.security.HeaderAuthenticationFilter;
import com.rtcdelivery.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import({
        SecurityConfig.class,
        HeaderAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class PaymentControllerTest {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    private PaymentResponse sampleResponse() {
        return new PaymentResponse(
                1L, 10L, 5L, 20000, "KRW", PaymentStatus.COMPLETED,
                "CARD", "MOCK", "MOCK-123456", "1234", null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("getPaymentByOrderId_비로그인은 401")
    void getPaymentByOrderId_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .param("orderId", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getPaymentByOrderId_로그인 사용자는 200 OK")
    void getPaymentByOrderId_authenticated_200() throws Exception {
        given(paymentService.getPaymentByOrderId(eq(10L), any()))
                .willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/payments")
                        .param("orderId", "10")
                        .header(HEADER_USER_ID, "5")
                        .header(HEADER_USER_ROLE, "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    @DisplayName("approvePayment_비로그인은 401")
    void approvePayment_unauthenticated_401() throws Exception {
        PaymentApproveRequest req = new PaymentApproveRequest("1234");

        mockMvc.perform(post("/api/v1/payments/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("approvePayment_로그인 사용자는 200 OK")
    void approvePayment_authenticated_200() throws Exception {
        PaymentApproveRequest req = new PaymentApproveRequest("1234");
        given(paymentService.approvePayment(eq(1L), any(), any()))
                .willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/payments/1/approve")
                        .header(HEADER_USER_ID, "5")
                        .header(HEADER_USER_ROLE, "ROLE_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }
}
