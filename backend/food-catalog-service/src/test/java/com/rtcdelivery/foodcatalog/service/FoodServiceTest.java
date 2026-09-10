package com.rtcdelivery.foodcatalog.service;

import com.rtcdelivery.foodcatalog.domain.Category;
import com.rtcdelivery.foodcatalog.domain.Food;
import com.rtcdelivery.foodcatalog.domain.Restaurant;
import com.rtcdelivery.foodcatalog.dto.request.FoodCreateRequest;
import com.rtcdelivery.foodcatalog.dto.request.FoodUpdateRequest;
import com.rtcdelivery.foodcatalog.dto.response.FoodResponse;
import com.rtcdelivery.foodcatalog.exception.BusinessException;
import com.rtcdelivery.foodcatalog.exception.ErrorCode;
import com.rtcdelivery.foodcatalog.repository.FoodRepository;
import com.rtcdelivery.foodcatalog.repository.RestaurantRepository;
import com.rtcdelivery.foodcatalog.security.Actor;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    private static final Long OWNER_ID = 100L;
    private static final Long RESTAURANT_ID = 1L;

    @Mock
    private FoodRepository foodRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantService restaurantService;

    @InjectMocks
    private FoodService foodService;

    private Restaurant restaurant;
    private Food food;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder()
                .id(RESTAURANT_ID)
                .ownerId(OWNER_ID)
                .category(Category.builder().id(1L).code("korean").name("한식").build())
                .name("서울 김치찌개")
                .address("서울시 강남구")
                .isActive(true)
                .build();

        food = Food.builder()
                .id(10L)
                .restaurant(restaurant)
                .name("김치찌개")
                .description("돼지고기와 묵은지")
                .price(9000)
                .build();
    }

    @Test
    @DisplayName("findByRestaurant_요청_locale의_번역본으로_응답한다")
    void findByRestaurant_withTranslation_returnsTranslatedName() {
        food.putTranslation("ja", "キムチチゲ", "豚肉と熟成キムチ");
        given(restaurantRepository.findByIdAndIsActiveTrue(RESTAURANT_ID)).willReturn(Optional.of(restaurant));
        given(foodRepository.findAllByRestaurantIdOrderByDisplayOrderAscIdAsc(RESTAURANT_ID))
                .willReturn(List.of(food));

        List<FoodResponse> result = foodService.findByRestaurant(RESTAURANT_ID, "ja");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("キムチチゲ");
    }

    @Test
    @DisplayName("findByRestaurant_비활성_음식점이면_RESTAURANT_NOT_FOUND")
    void findByRestaurant_inactiveRestaurant_throwsNotFound() {
        given(restaurantRepository.findByIdAndIsActiveTrue(RESTAURANT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> foodService.findByRestaurant(RESTAURANT_ID, "ko"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    @DisplayName("create_소유권_검증을_통과하면_메뉴를_저장한다")
    void create_ownedRestaurant_savesFood() {
        Actor actor = new Actor(OWNER_ID, false);
        given(restaurantService.findOwned(RESTAURANT_ID, actor)).willReturn(restaurant);
        given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));

        FoodCreateRequest request = FoodCreateRequest.builder()
                .name("된장찌개")
                .price(8500)
                .build();

        FoodResponse result = foodService.create(RESTAURANT_ID, request, actor, "ko");

        assertThat(result.name()).isEqualTo("된장찌개");
        assertThat(result.price()).isEqualTo(8500);
    }

    @Test
    @DisplayName("create_소유권_검증에_실패하면_저장하지_않는다")
    void create_notOwned_doesNotSave() {
        Actor actor = new Actor(999L, false);
        willThrow(new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND))
                .given(restaurantService).findOwned(RESTAURANT_ID, actor);

        FoodCreateRequest request = FoodCreateRequest.builder().name("된장찌개").price(8500).build();

        assertThatThrownBy(() -> foodService.create(RESTAURANT_ID, request, actor, "ko"))
                .isInstanceOf(BusinessException.class);

        verify(foodRepository, never()).save(any(Food.class));
    }

    @Test
    @DisplayName("update_다른_음식점의_메뉴_ID를_넘기면_FOOD_NOT_FOUND")
    void update_foodFromAnotherRestaurant_throwsNotFound() {
        Actor actor = new Actor(OWNER_ID, false);
        given(restaurantService.findOwned(RESTAURANT_ID, actor)).willReturn(restaurant);
        given(foodRepository.findByIdAndRestaurantId(999L, RESTAURANT_ID)).willReturn(Optional.empty());

        FoodUpdateRequest request = FoodUpdateRequest.builder().name("탈취 시도").build();

        assertThatThrownBy(() -> foodService.update(RESTAURANT_ID, 999L, request, actor, "ko"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FOOD_NOT_FOUND);
    }

    @Test
    @DisplayName("update_원본_이름이_바뀌면_기존_번역을_폐기한다")
    void update_sourceNameChanged_invalidatesTranslations() {
        food.putTranslation("ja", "キムチチゲ", "豚肉と熟成キムチ");
        Actor actor = new Actor(OWNER_ID, false);
        given(restaurantService.findOwned(RESTAURANT_ID, actor)).willReturn(restaurant);
        given(foodRepository.findByIdAndRestaurantId(10L, RESTAURANT_ID)).willReturn(Optional.of(food));

        FoodUpdateRequest request = FoodUpdateRequest.builder().name("김치찌개(대)").build();
        foodService.update(RESTAURANT_ID, 10L, request, actor, "ko");

        assertThat(food.getTranslations()).isEmpty();
    }

    @Test
    @DisplayName("update_가격만_바꾸면_번역을_유지한다")
    void update_priceOnly_keepsTranslations() {
        food.putTranslation("ja", "キムチチゲ", "豚肉と熟成キムチ");
        Actor actor = new Actor(OWNER_ID, false);
        given(restaurantService.findOwned(RESTAURANT_ID, actor)).willReturn(restaurant);
        given(foodRepository.findByIdAndRestaurantId(10L, RESTAURANT_ID)).willReturn(Optional.of(food));

        FoodUpdateRequest request = FoodUpdateRequest.builder().price(11000).build();
        foodService.update(RESTAURANT_ID, 10L, request, actor, "ko");

        assertThat(food.getTranslations()).hasSize(1);
        assertThat(food.getPrice()).isEqualTo(11000);
    }

    @Test
    @DisplayName("update_soldOut_플래그를_반영한다")
    void update_soldOutFlag_isApplied() {
        Actor actor = new Actor(OWNER_ID, false);
        given(restaurantService.findOwned(RESTAURANT_ID, actor)).willReturn(restaurant);
        given(foodRepository.findByIdAndRestaurantId(10L, RESTAURANT_ID)).willReturn(Optional.of(food));

        FoodUpdateRequest request = FoodUpdateRequest.builder().soldOut(true).build();
        FoodResponse result = foodService.update(RESTAURANT_ID, 10L, request, actor, "ko");

        assertThat(result.soldOut()).isTrue();
    }

    @Test
    @DisplayName("delete_소유권_검증을_통과하면_메뉴를_삭제한다")
    void delete_ownedRestaurant_deletesFood() {
        Actor actor = new Actor(OWNER_ID, false);
        given(restaurantService.findOwned(RESTAURANT_ID, actor)).willReturn(restaurant);
        given(foodRepository.findByIdAndRestaurantId(10L, RESTAURANT_ID)).willReturn(Optional.of(food));

        foodService.delete(RESTAURANT_ID, 10L, actor);

        verify(foodRepository).delete(food);
    }
}
