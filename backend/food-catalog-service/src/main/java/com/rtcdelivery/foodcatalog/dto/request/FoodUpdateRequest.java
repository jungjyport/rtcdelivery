package com.rtcdelivery.foodcatalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부분 수정({@code PATCH}) 요청. {@code null}인 필드는 변경하지 않는다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "메뉴 수정 요청 DTO")
public class FoodUpdateRequest {

    @Size(max = 255, message = "메뉴명은 최대 255자입니다.")
    @Schema(description = "메뉴명 (한국어 원본)", example = "김치찌개")
    private String name;

    @Schema(description = "메뉴 설명 (한국어 원본)")
    private String description;

    @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
    @Schema(description = "가격 (원)", example = "9000")
    private Integer price;

    @Size(max = 500, message = "이미지 URL은 최대 500자입니다.")
    @Schema(description = "메뉴 이미지 URL")
    private String imageUrl;

    @Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
    @Schema(description = "정렬 순서", example = "1")
    private Integer displayOrder;

    @Schema(description = "품절 여부", example = "false")
    private Boolean soldOut;
}
