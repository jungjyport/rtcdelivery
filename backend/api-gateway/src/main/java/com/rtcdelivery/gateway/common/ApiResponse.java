package com.rtcdelivery.gateway.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통 API 응답 래퍼.
 *
 * <p>Gateway는 도메인 응답을 만들지 않지만, 라우팅 이전 단계에서 발생한 에러(인증 실패, 다운스트림 장애 등)를
 * 하위 서비스와 <b>동일한 형태</b>로 내려주기 위해 필요하다. 프론트엔드 API Client는 이 형태만 파싱한다.
 *
 * <p>{@code data}는 값이 {@code null}이어도 직렬화되어야 한다. 자세한 내용은 docs/error-handling.md §2.2 참조.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;

    /**
     * @param message SCREAMING_SNAKE_CASE 에러 코드. 사용자 문장을 넣지 않는다.
     */
    public static <T> ApiResponse<T> error(int status, String message) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .data(null)
                .build();
    }
}
