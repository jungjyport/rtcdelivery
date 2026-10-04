package com.rtcdelivery.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "페이지네이션 응답")
public record PageResponse<T>(
        @Schema(description = "현재 페이지 내용") List<T> content,
        @Schema(description = "현재 페이지 번호 (0-based)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "10") int size,
        @Schema(description = "전체 요소 수", example = "42") long totalElements,
        @Schema(description = "전체 페이지 수", example = "5") int totalPages
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
