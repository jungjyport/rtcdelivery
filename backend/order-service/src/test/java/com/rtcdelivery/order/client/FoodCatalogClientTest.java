package com.rtcdelivery.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.order.client.dto.OrderSnapshotResponse;
import com.rtcdelivery.order.common.ApiResponse;
import com.rtcdelivery.order.exception.BusinessException;
import com.rtcdelivery.order.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FoodCatalogClientTest {

    private FoodCatalogClient foodCatalogClient;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        foodCatalogClient = new FoodCatalogClient(builder);
    }

    @Test
    @DisplayName("정상 조회 시 OrderSnapshotResponse 반환")
    void getOrderSnapshot_success() throws Exception {
        OrderSnapshotResponse snapshot = new OrderSnapshotResponse(
                1L, 10L, true, "맛있는 치킨", 3000, 15000, Collections.emptyList()
        );

        ApiResponse<OrderSnapshotResponse> apiResponse = ApiResponse.success(snapshot);

        mockServer.expect(requestTo("http://food-catalog-service/internal/restaurants/1/order-snapshot"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(apiResponse), MediaType.APPLICATION_JSON));

        OrderSnapshotResponse result = foodCatalogClient.getOrderSnapshot(1L);

        assertThat(result).isNotNull();
        assertThat(result.restaurantId()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("맛있는 치킨");
        mockServer.verify();
    }

    @Test
    @DisplayName("404 반환 시 RESTAURANT_NOT_FOUND 예외 발생")
    void getOrderSnapshot_notFound() {
        mockServer.expect(requestTo("http://food-catalog-service/internal/restaurants/999/order-snapshot"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> foodCatalogClient.getOrderSnapshot(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    @Test
    @DisplayName("500 등 기타 오류 시 UPSTREAM_SERVICE_ERROR 예외 발생")
    void getOrderSnapshot_upstreamError() {
        mockServer.expect(requestTo("http://food-catalog-service/internal/restaurants/1/order-snapshot"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> foodCatalogClient.getOrderSnapshot(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR));
    }

    @Test
    @DisplayName("fallbackSnapshot 호출 시 BusinessException은 그대로 던지고 일반 예외는 UPSTREAM_SERVICE_ERROR로 감싼다")
    void fallbackSnapshot() {
        BusinessException be = new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
        assertThatThrownBy(() -> foodCatalogClient.fallbackSnapshot(1L, be))
                .isSameAs(be);

        RuntimeException ex = new RuntimeException("connection timeout");
        assertThatThrownBy(() -> foodCatalogClient.fallbackSnapshot(1L, ex))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR));
    }
}
