package com.rtcdelivery.memberauth.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통 API 응답 래퍼.
 *
 * <p>{@code data}는 값이 {@code null}이어도 직렬화되어야 한다. 프론트엔드 API Client가
 * {@code data} 키의 존재 여부로 이 래퍼인지 판정하므로, {@code @JsonInclude(NON_NULL)}을
 * 붙이면 에러 응답에서 에러 코드가 유실된다. 자세한 내용은 docs/error-handling.md §2.2 참조.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .status(201)
                .message("Created")
                .data(data)
                .build();
    }

    /**
     * @param message SCREAMING_SNAKE_CASE 에러 코드. 사용자 문장을 넣지 않는다.
     */
    public static <T> ApiResponse<T> error(int status, String message) {
        return error(status, message, null);
    }

    /**
     * @param data 실패 상세. 검증 실패의 필드별 메시지 등 개발자용 정보만 담는다.
     */
    public static <T> ApiResponse<T> error(int status, String message, T data) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .data(data)
                .build();
    }
}
