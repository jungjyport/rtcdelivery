package com.rtcdelivery.foodcatalog.service;

import com.rtcdelivery.foodcatalog.domain.Food;
import com.rtcdelivery.foodcatalog.domain.Restaurant;
import com.rtcdelivery.foodcatalog.dto.request.FoodCreateRequest;
import com.rtcdelivery.foodcatalog.dto.request.FoodUpdateRequest;
import com.rtcdelivery.foodcatalog.dto.response.FoodResponse;
import com.rtcdelivery.foodcatalog.dto.response.PageResponse;
import com.rtcdelivery.foodcatalog.exception.BusinessException;
import com.rtcdelivery.foodcatalog.exception.ErrorCode;
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

/**
 * 메뉴는 자체 소유권을 갖지 않는다. 쓰기 인가는 모두 소속 음식점의 소유권을 따르므로
 * {@link RestaurantService#findOwned}에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodService {

    private final FoodRepository foodRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;

    @Transactional(readOnly = true)
    public List<FoodResponse> findByRestaurant(Long restaurantId, String locale) {
        if (!restaurantRepository.findByIdAndIsActiveTrue(restaurantId).isPresent()) {
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
        }

        return foodRepository.findAllByRestaurantIdOrderByDisplayOrderAscIdAsc(restaurantId).stream()
                .map(food -> FoodResponse.of(food, locale))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<FoodResponse> search(Long restaurantId,
                                             String keyword,
                                             Pageable pageable,
                                             String locale) {
        Page<Food> page = foodRepository.search(restaurantId, normalize(keyword), locale, pageable);
        return PageResponse.of(page, food -> FoodResponse.of(food, locale));
    }

    @Transactional
    public FoodResponse create(Long restaurantId, FoodCreateRequest request, Actor actor, String locale) {
        Restaurant restaurant = restaurantService.findOwned(restaurantId, actor);

        Food food = Food.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrl(request.getImageUrl())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isSoldOut(false)
                .build();

        Food saved = foodRepository.save(food);
        log.info("Food created: id={}, restaurantId={}", saved.getId(), restaurantId);

        return FoodResponse.of(saved, locale);
    }

    @Transactional
    public FoodResponse update(Long restaurantId,
                               Long foodId,
                               FoodUpdateRequest request,
                               Actor actor,
                               String locale) {
        restaurantService.findOwned(restaurantId, actor);
        Food food = findInRestaurant(foodId, restaurantId);

        boolean originalTextChanged =
                changed(request.getName(), food.getName())
                        || changed(request.getDescription(), food.getDescription());

        food.update(
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getImageUrl(),
                request.getDisplayOrder()
        );

        if (request.getSoldOut() != null) {
            food.changeSoldOut(request.getSoldOut());
        }

        if (originalTextChanged) {
            // 기존 번역은 옛 원문을 가리키므로 버린다. 재번역이 도착할 때까지 원본(ko)으로 폴백된다.
            food.clearTranslations();
            log.info("Food translations invalidated by source text change: id={}", foodId);
        }

        return FoodResponse.of(food, locale);
    }

    /**
     * 메뉴는 주문 이력이 스냅샷(이름·가격)을 보관하는 것을 전제로 물리 삭제한다.
     */
    @Transactional
    public void delete(Long restaurantId, Long foodId, Actor actor) {
        restaurantService.findOwned(restaurantId, actor);
        Food food = findInRestaurant(foodId, restaurantId);

        foodRepository.delete(food);
        log.info("Food deleted: id={}, restaurantId={}, by memberId={}", foodId, restaurantId, actor.memberId());
    }

    /**
     * 다른 음식점의 메뉴 ID를 넘겨 남의 메뉴를 건드리는 것을 막기 위해 음식점 ID까지 함께 조회한다.
     */
    private Food findInRestaurant(Long foodId, Long restaurantId) {
        return foodRepository.findByIdAndRestaurantId(foodId, restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));
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
