package com.rtcdelivery.foodcatalog.service;

import com.rtcdelivery.foodcatalog.domain.Category;
import com.rtcdelivery.foodcatalog.dto.request.CategoryCreateRequest;
import com.rtcdelivery.foodcatalog.dto.request.CategoryUpdateRequest;
import com.rtcdelivery.foodcatalog.dto.response.CategoryResponse;
import com.rtcdelivery.foodcatalog.exception.BusinessException;
import com.rtcdelivery.foodcatalog.exception.ErrorCode;
import com.rtcdelivery.foodcatalog.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        if (categoryRepository.existsByCode(request.getCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_CODE);
        }

        Category category = Category.builder()
                .code(request.getCode())
                .name(request.getName())
                .imageUrl(request.getImageUrl())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isActive(true)
                .build();

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long categoryId, CategoryUpdateRequest request) {
        Category category = findActiveById(categoryId);
        category.update(request.getName(), request.getImageUrl(), request.getDisplayOrder());
        return CategoryResponse.from(category);
    }

    /**
     * 카테고리는 음식점이 참조하므로 물리 삭제하지 않고 비활성화한다.
     */
    @Transactional
    public void deactivate(Long categoryId) {
        Category category = findActiveById(categoryId);
        category.deactivate();
        log.info("Category deactivated: id={}, code={}", categoryId, category.getCode());
    }

    private Category findActiveById(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!category.isActive()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }
}
