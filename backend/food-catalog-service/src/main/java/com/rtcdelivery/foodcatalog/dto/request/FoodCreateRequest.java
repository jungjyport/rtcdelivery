package com.rtcdelivery.foodcatalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "메뉴 등록 요청 DTO")
public class FoodCreateRequest {

    @NotBlank(message = "메뉴명은 필수입니다.")
    @Size(max = 255, message = "메뉴명은 최대 255자입니다.")
    @Schema(description = "메뉴명 (한국어 원본)", example = "김치찌개")
    private String name;

    @Schema(description = "메뉴 설명 (한국어 원본)", example = "돼지고기와 묵은지를 넣고 끓인 찌개")
    private String description;

    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
    @Schema(description = "가격 (원)", example = "9000")
    private Integer price;

    @Size(max = 500, message = "이미지 URL은 최대 500자입니다.")
    @Schema(description = "메뉴 이미지 URL")
    private String imageUrl;

    @Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
    @Schema(description = "정렬 순서", example = "1")
    private Integer displayOrder;
}
