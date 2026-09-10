package com.rtcdelivery.foodcatalog.controller;

import com.rtcdelivery.foodcatalog.common.ApiResponse;
import com.rtcdelivery.foodcatalog.common.SupportedLocale;
import com.rtcdelivery.foodcatalog.dto.request.FoodCreateRequest;
import com.rtcdelivery.foodcatalog.dto.request.FoodUpdateRequest;
import com.rtcdelivery.foodcatalog.dto.request.RestaurantCreateRequest;
import com.rtcdelivery.foodcatalog.dto.request.RestaurantUpdateRequest;
import com.rtcdelivery.foodcatalog.dto.response.FoodResponse;
import com.rtcdelivery.foodcatalog.dto.response.PageResponse;
import com.rtcdelivery.foodcatalog.dto.response.RestaurantDetailResponse;
import com.rtcdelivery.foodcatalog.dto.response.RestaurantResponse;
import com.rtcdelivery.foodcatalog.security.Actor;
import com.rtcdelivery.foodcatalog.service.FoodService;
import com.rtcdelivery.foodcatalog.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 음식점과 그 하위 메뉴 API.
 *
 * <p>조회는 비로그인 사용자에게도 열려 있고, 쓰기는 점주와 관리자만 가능하다. 역할 검사를 통과해도
 * 서비스 계층에서 소유권을 한 번 더 확인하므로, 점주가 남의 가게를 수정할 수는 없다.
 */
@Tag(name = "Restaurant", description = "음식점 · 메뉴 API")
@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final FoodService foodService;

    @Operation(summary = "음식점 목록 조회 / 검색",
            description = "활성 음식점을 페이지로 반환합니다. `keyword`는 원본명과 요청 locale의 "
                    + "번역명을 모두 검색합니다. 응답 텍스트는 `Accept-Language`를 따르며, 해당 "
                    + "locale의 번역이 없으면 원본(ko)으로 폴백합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RestaurantResponse>>> search(
            @Parameter(description = "카테고리 ID (선택)") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "검색어 (선택)") @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<RestaurantResponse> response =
                restaurantService.search(categoryId, keyword, pageable, SupportedLocale.current());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "음식점 상세 조회", description = "음식점 정보와 메뉴 목록을 함께 반환합니다.")
    @GetMapping("/{restaurantId}")
    public ResponseEntity<ApiResponse<RestaurantDetailResponse>> getDetail(@PathVariable Long restaurantId) {
        RestaurantDetailResponse response =
                restaurantService.getDetail(restaurantId, SupportedLocale.current());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "음식점 등록",
            description = "점주 또는 관리자만 호출할 수 있습니다. 텍스트는 한국어 원본으로 등록하고, "
                    + "다른 언어는 translation-service가 비동기로 채웁니다.")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<RestaurantResponse>> create(
            @Valid @RequestBody RestaurantCreateRequest request,
            Authentication authentication) {

        RestaurantResponse response = restaurantService.create(
                request, Actor.from(authentication), SupportedLocale.current());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "음식점 수정",
            description = "소유 점주 또는 관리자만 호출할 수 있습니다. 이름이나 설명이 바뀌면 기존 번역은 폐기됩니다. "
                    + "소유자가 아닌 경우 리소스 존재 여부를 감추기 위해 404를 반환합니다.")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PatchMapping("/{restaurantId}")
    public ResponseEntity<ApiResponse<RestaurantResponse>> update(
            @PathVariable Long restaurantId,
            @Valid @RequestBody RestaurantUpdateRequest request,
            Authentication authentication) {

        RestaurantResponse response = restaurantService.update(
                restaurantId, request, Actor.from(authentication), SupportedLocale.current());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "음식점 비활성화",
            description = "소유 점주 또는 관리자만 호출할 수 있습니다. 주문 이력이 참조하므로 물리 삭제하지 않습니다.")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @DeleteMapping("/{restaurantId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long restaurantId,
                                                    Authentication authentication) {

        restaurantService.delete(restaurantId, Actor.from(authentication));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "음식점 메뉴 목록 조회")
    @GetMapping("/{restaurantId}/foods")
    public ResponseEntity<ApiResponse<List<FoodResponse>>> findFoods(@PathVariable Long restaurantId) {
        List<FoodResponse> response = foodService.findByRestaurant(restaurantId, SupportedLocale.current());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "메뉴 등록", description = "소유 점주 또는 관리자만 호출할 수 있습니다.")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping("/{restaurantId}/foods")
    public ResponseEntity<ApiResponse<FoodResponse>> createFood(
            @PathVariable Long restaurantId,
            @Valid @RequestBody FoodCreateRequest request,
            Authentication authentication) {

        FoodResponse response = foodService.create(
                restaurantId, request, Actor.from(authentication), SupportedLocale.current());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "메뉴 수정", description = "소유 점주 또는 관리자만 호출할 수 있습니다.")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PatchMapping("/{restaurantId}/foods/{foodId}")
    public ResponseEntity<ApiResponse<FoodResponse>> updateFood(
            @PathVariable Long restaurantId,
            @PathVariable Long foodId,
            @Valid @RequestBody FoodUpdateRequest request,
            Authentication authentication) {

        FoodResponse response = foodService.update(
                restaurantId, foodId, request, Actor.from(authentication), SupportedLocale.current());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "메뉴 삭제", description = "소유 점주 또는 관리자만 호출할 수 있습니다.")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @DeleteMapping("/{restaurantId}/foods/{foodId}")
    public ResponseEntity<ApiResponse<Void>> deleteFood(@PathVariable Long restaurantId,
                                                        @PathVariable Long foodId,
                                                        Authentication authentication) {

        foodService.delete(restaurantId, foodId, Actor.from(authentication));
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
