package com.rtcdelivery.foodcatalog.service;

import com.rtcdelivery.foodcatalog.domain.Category;
import com.rtcdelivery.foodcatalog.domain.Food;
import com.rtcdelivery.foodcatalog.domain.Restaurant;
import com.rtcdelivery.foodcatalog.dto.request.RestaurantCreateRequest;
import com.rtcdelivery.foodcatalog.dto.request.RestaurantUpdateRequest;
import com.rtcdelivery.foodcatalog.dto.response.PageResponse;
import com.rtcdelivery.foodcatalog.dto.response.RestaurantDetailResponse;
import com.rtcdelivery.foodcatalog.dto.response.RestaurantResponse;
import com.rtcdelivery.foodcatalog.exception.BusinessException;
import com.rtcdelivery.foodcatalog.exception.ErrorCode;
import com.rtcdelivery.foodcatalog.repository.CategoryRepository;
import com.rtcdelivery.foodcatalog.repository.FoodRepository;
import com.rtcdelivery.foodcatalog.repository.RestaurantRepository;
import com.rtcdelivery.foodcatalog.security.Actor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final FoodRepository foodRepository;

    @Transactional(readOnly = true)
    public PageResponse<RestaurantResponse> search(Long categoryId,
                                                   String keyword,
                                                   Pageable pageable,
                                                   String locale) {
        Page<Restaurant> page = restaurantRepository.search(categoryId, normalize(keyword), locale, pageable);
        return PageResponse.of(page, restaurant -> RestaurantResponse.of(restaurant, locale));
    }

    @Transactional(readOnly = true)
    public RestaurantDetailResponse getDetail(Long restaurantId, String locale) {
        Restaurant restaurant = findActiveById(restaurantId);
        List<Food> foods = foodRepository.findAllByRestaurantIdOrderByDisplayOrderAscIdAsc(restaurantId);
        return RestaurantDetailResponse.of(restaurant, foods, locale);
    }

    @Transactional
    public RestaurantResponse create(RestaurantCreateRequest request, Actor actor, String locale) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        Restaurant restaurant = Restaurant.builder()
                .ownerId(actor.memberId())
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .address(request.getAddress())
                .phoneNumber(request.getPhoneNumber())
                .deliveryFee(request.getDeliveryFee() != null ? request.getDeliveryFee() : 0)
                .minOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount() : 0)
                .imageUrl(request.getImageUrl())
                .isActive(true)
                .build();

        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Restaurant created: id={}, ownerId={}", saved.getId(), actor.memberId());

        return RestaurantResponse.of(saved, locale);
    }

    @Transactional
    public RestaurantResponse update(Long restaurantId,
                                     RestaurantUpdateRequest request,
                                     Actor actor,
                                     String locale) {
        Restaurant restaurant = findOwned(restaurantId, actor);

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        boolean originalTextChanged =
                changed(request.getName(), restaurant.getName())
                        || changed(request.getDescription(), restaurant.getDescription());

        restaurant.update(
                category,
                request.getName(),
                request.getDescription(),
                request.getAddress(),
                request.getPhoneNumber(),
                request.getDeliveryFee(),
                request.getMinOrderAmount(),
                request.getImageUrl()
        );

        if (originalTextChanged) {
            // 기존 번역은 옛 원문을 가리키므로 버린다. 재번역이 도착할 때까지 원본(ko)으로 폴백된다.
            restaurant.clearTranslations();
            log.info("Restaurant translations invalidated by source text change: id={}", restaurantId);
        }

        return RestaurantResponse.of(restaurant, locale);
    }

    /**
     * 주문 이력이 음식점을 참조하므로 물리 삭제하지 않고 비활성화한다.
     */
    @Transactional
    public void delete(Long restaurantId, Actor actor) {
        Restaurant restaurant = findOwned(restaurantId, actor);
        restaurant.deactivate();
        log.info("Restaurant deactivated: id={}, by memberId={}", restaurantId, actor.memberId());
    }

    /**
     * 소유권을 확인하고 음식점을 반환한다. 관리자는 통과시킨다.
     *
     * <p>소유자가 아닐 때 403이 아니라 404를 던진다. 403은 "그 ID의 음식점이 존재한다"는 사실을
     * 노출해 리소스 열거를 허용하기 때문이다.
     */
    Restaurant findOwned(Long restaurantId, Actor actor) {
        Restaurant restaurant = findActiveById(restaurantId);

        if (!actor.admin() && !restaurant.isOwnedBy(actor.memberId())) {
            log.warn("Ownership check failed: restaurantId={}, actorId={}", restaurantId, actor.memberId());
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
        return restaurant;
    }

    private Restaurant findActiveById(Long restaurantId) {
        return restaurantRepository.findByIdAndIsActiveTrue(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    private static String normalize(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean changed(String incoming, String current) {
        return incoming != null && !incoming.equals(current);
    }
}
