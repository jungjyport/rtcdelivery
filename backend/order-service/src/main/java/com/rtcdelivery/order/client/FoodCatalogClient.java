package com.rtcdelivery.order.client;

import com.rtcdelivery.order.client.dto.OrderSnapshotResponse;
import com.rtcdelivery.order.common.ApiResponse;
import com.rtcdelivery.order.exception.BusinessException;
import com.rtcdelivery.order.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class FoodCatalogClient {

    private final RestClient restClient;

    public FoodCatalogClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("http://food-catalog-service")
                .build();
    }

    @Retry(name = "foodCatalogClient", fallbackMethod = "fallbackSnapshot")
    @CircuitBreaker(name = "foodCatalogClient", fallbackMethod = "fallbackSnapshot")
    public OrderSnapshotResponse getOrderSnapshot(Long restaurantId) {
        ApiResponse<OrderSnapshotResponse> response = restClient.get()
                .uri("/internal/restaurants/{restaurantId}/order-snapshot", restaurantId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    if (resp.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                        throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
                    }
                    throw new BusinessException(ErrorCode.UPSTREAM_SERVICE_ERROR);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new BusinessException(ErrorCode.UPSTREAM_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<ApiResponse<OrderSnapshotResponse>>() {});

        if (response == null || response.getData() == null) {
            throw new BusinessException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }

        return response.getData();
    }

    public OrderSnapshotResponse fallbackSnapshot(Long restaurantId, Throwable t) {
        if (t instanceof BusinessException be) {
            throw be;
        }
        log.error("FoodCatalogClient fallback triggered for restaurantId={}, error={}", restaurantId, t.getMessage());
        throw new BusinessException(ErrorCode.UPSTREAM_SERVICE_ERROR);
    }
}
