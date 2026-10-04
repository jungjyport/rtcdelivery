package com.rtcdelivery.payment.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("ApiResponse 헬퍼 메서드 정상 생성 확인")
    void apiResponse_helpers() {
        ApiResponse<String> res1 = ApiResponse.success("hello");
        assertThat(res1.getStatus()).isEqualTo(200);
        assertThat(res1.getMessage()).isEqualTo("Success");
        assertThat(res1.getData()).isEqualTo("hello");

        ApiResponse<String> res2 = ApiResponse.success("CustomSuccess", "world");
        assertThat(res2.getStatus()).isEqualTo(200);
        assertThat(res2.getMessage()).isEqualTo("CustomSuccess");
        assertThat(res2.getData()).isEqualTo("world");

        ApiResponse<String> res3 = ApiResponse.created("createdData");
        assertThat(res3.getStatus()).isEqualTo(201);
        assertThat(res3.getMessage()).isEqualTo("Created");
        assertThat(res3.getData()).isEqualTo("createdData");

        ApiResponse<Void> res4 = ApiResponse.error(400, "BAD_REQUEST");
        assertThat(res4.getStatus()).isEqualTo(400);
        assertThat(res4.getMessage()).isEqualTo("BAD_REQUEST");
        assertThat(res4.getData()).isNull();
    }
}
