package com.rtcdelivery.foodcatalog.controller;

import com.rtcdelivery.foodcatalog.common.ApiResponse;
import com.rtcdelivery.foodcatalog.common.SupportedLocale;
import com.rtcdelivery.foodcatalog.dto.response.FoodResponse;
import com.rtcdelivery.foodcatalog.dto.response.PageResponse;
import com.rtcdelivery.foodcatalog.service.FoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 음식점을 가로지르는 메뉴 검색. 특정 음식점의 메뉴 목록은
 * {@code GET /api/v1/restaurants/{restaurantId}/foods}를 쓴다.
 */
@Tag(name = "Food", description = "메뉴 검색 API")
@RestController
@RequestMapping("/api/v1/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @Operation(summary = "메뉴 검색",
            description = "`keyword`는 원본명과 요청 locale의 번역명을 모두 검색합니다. "
                    + "비활성 음식점의 메뉴는 제외됩니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<FoodResponse>>> search(
            @Parameter(description = "음식점 ID (선택)") @RequestParam(required = false) Long restaurantId,
            @Parameter(description = "검색어 (선택)") @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<FoodResponse> response =
                foodService.search(restaurantId, keyword, pageable, SupportedLocale.current());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
