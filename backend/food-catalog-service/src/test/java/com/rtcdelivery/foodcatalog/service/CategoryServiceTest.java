package com.rtcdelivery.foodcatalog.service;

import com.rtcdelivery.foodcatalog.domain.Category;
import com.rtcdelivery.foodcatalog.dto.request.CategoryCreateRequest;
import com.rtcdelivery.foodcatalog.dto.request.CategoryUpdateRequest;
import com.rtcdelivery.foodcatalog.dto.response.CategoryResponse;
import com.rtcdelivery.foodcatalog.exception.BusinessException;
import com.rtcdelivery.foodcatalog.exception.ErrorCode;
import com.rtcdelivery.foodcatalog.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("findAll_활성_카테고리를_정렬_순서대로_반환한다")
    void findAll_returnsActiveCategoriesInOrder() {
        given(categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc()).willReturn(List.of(
                Category.builder().id(1L).code("korean").name("한식").displayOrder(1).isActive(true).build(),
                Category.builder().id(2L).code("chinese").name("중식").displayOrder(2).isActive(true).build()
        ));

        List<CategoryResponse> result = categoryService.findAll();

        assertThat(result).extracting(CategoryResponse::code).containsExactly("korean", "chinese");
    }

    @Test
    @DisplayName("create_중복_코드는_DUPLICATE_CATEGORY_CODE이고_저장하지_않는다")
    void create_duplicateCode_throwsAndDoesNotSave() {
        given(categoryRepository.existsByCode("korean")).willReturn(true);

        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .code("korean")
                .name("한식")
                .build();

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_CATEGORY_CODE);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("create_displayOrder를_생략하면_0으로_저장한다")
    void create_withoutDisplayOrder_defaultsToZero() {
        given(categoryRepository.existsByCode("korean")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willAnswer(inv -> inv.getArgument(0));

        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .code("korean")
                .name("한식")
                .build();

        CategoryResponse result = categoryService.create(request);

        assertThat(result.displayOrder()).isZero();
    }

    @Test
    @DisplayName("update_null_필드는_기존_값을_유지한다")
    void update_nullFields_keepExistingValues() {
        Category category = Category.builder()
                .id(1L).code("korean").name("한식").displayOrder(1).isActive(true).build();
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        CategoryResponse result = categoryService.update(
                1L, CategoryUpdateRequest.builder().displayOrder(5).build());

        assertThat(result.name()).isEqualTo("한식");
        assertThat(result.displayOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("update_이미_비활성인_카테고리는_CATEGORY_NOT_FOUND")
    void update_inactiveCategory_throwsNotFound() {
        Category category = Category.builder()
                .id(1L).code("korean").name("한식").isActive(false).build();
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        CategoryUpdateRequest request = CategoryUpdateRequest.builder().name("변경").build();

        assertThatThrownBy(() -> categoryService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("deactivate_물리_삭제하지_않고_비활성화한다")
    void deactivate_marksInactiveWithoutDeleting() {
        Category category = Category.builder()
                .id(1L).code("korean").name("한식").isActive(true).build();
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        categoryService.deactivate(1L);

        assertThat(category.isActive()).isFalse();
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    @DisplayName("deactivate_존재하지_않는_카테고리는_CATEGORY_NOT_FOUND")
    void deactivate_unknownCategory_throwsNotFound() {
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deactivate(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }
}
