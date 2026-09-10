package com.rtcdelivery.foodcatalog.controller;

import com.rtcdelivery.foodcatalog.common.ApiResponse;
import com.rtcdelivery.foodcatalog.dto.request.CategoryCreateRequest;
import com.rtcdelivery.foodcatalog.dto.request.CategoryUpdateRequest;
import com.rtcdelivery.foodcatalog.dto.response.CategoryResponse;
import com.rtcdelivery.foodcatalog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Category", description = "음식 카테고리 API")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "카테고리 목록 조회",
            description = "활성 카테고리를 정렬 순서대로 반환합니다. 표시 문구는 응답의 `code`를 "
                    + "프론트엔드 i18n 키(`category.{code}`)로 변환해 표출합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.findAll()));
    }

    @Operation(summary = "카테고리 생성", description = "관리자만 호출할 수 있습니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryCreateRequest request) {

        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "카테고리 수정",
            description = "관리자만 호출할 수 있습니다. `code`는 프론트 i18n 키와의 계약이므로 변경할 수 없습니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.success(categoryService.update(categoryId, request)));
    }

    @Operation(summary = "카테고리 비활성화",
            description = "관리자만 호출할 수 있습니다. 음식점이 참조하므로 물리 삭제하지 않고 목록에서만 제외합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long categoryId) {
        categoryService.deactivate(categoryId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
