package com.rtcdelivery.foodcatalog.service;

import com.rtcdelivery.foodcatalog.domain.Category;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    private static final Long OWNER_ID = 100L;
    private static final Long OTHER_OWNER_ID = 200L;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private FoodRepository foodRepository;

    @InjectMocks
    private RestaurantService restaurantService;

    private Category category;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        category = Category.builder().id(1L).code("korean").name("한식").isActive(true).build();
        restaurant = Restaurant.builder()
                .id(1L)
                .ownerId(OWNER_ID)
                .category(category)
                .name("서울 김치찌개")
                .description("30년 전통")
                .address("서울시 강남구")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("search_요청_locale의_번역본으로_응답한다")
    void search_withTranslation_returnsTranslatedName() {
        restaurant.putTranslation("ja", "ソウルキムチチゲ", "30年伝統");
        Pageable pageable = PageRequest.of(0, 10);
        given(restaurantRepository.search(null, null, "ja", pageable))
                .willReturn(new PageImpl<>(List.of(restaurant), pageable, 1));

        PageResponse<RestaurantResponse> result = restaurantService.search(null, null, pageable, "ja");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("ソウルキムチチゲ");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("search_공백만_있는_키워드는_조건에서_제외한다")
    void search_blankKeyword_treatedAsNoKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        given(restaurantRepository.search(null, null, "ko", pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        restaurantService.search(null, "   ", pageable, "ko");

        verify(restaurantRepository).search(null, null, "ko", pageable);
    }

    @Test
    @DisplayName("getDetail_음식점과_메뉴_목록을_함께_반환한다")
    void getDetail_returnsRestaurantWithFoods() {
        given(restaurantRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(restaurant));
        given(foodRepository.findAllByRestaurantIdOrderByDisplayOrderAscIdAsc(1L)).willReturn(List.of());

        RestaurantDetailResponse result = restaurantService.getDetail(1L, "ko");

        assertThat(result.restaurant().name()).isEqualTo("서울 김치찌개");
        assertThat(result.foods()).isEmpty();
    }

    @Test
    @DisplayName("getDetail_비활성_음식점은_RESTAURANT_NOT_FOUND")
    void getDetail_inactiveRestaurant_throwsNotFound() {
        given(restaurantRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.getDetail(1L, "ko"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    @DisplayName("create_등록자를_소유자로_기록한다")
    void create_setsActorAsOwner() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(restaurantRepository.save(any(Restaurant.class))).willAnswer(inv -> inv.getArgument(0));

        RestaurantCreateRequest request = RestaurantCreateRequest.builder()
                .categoryId(1L)
                .name("새 가게")
                .address("서울시 종로구")
                .build();

        restaurantService.create(request, new Actor(OWNER_ID, false), "ko");

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(OWNER_ID);
    }

    @Test
    @DisplayName("create_존재하지_않는_카테고리는_CATEGORY_NOT_FOUND")
    void create_unknownCategory_throwsNotFound() {
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        RestaurantCreateRequest request = RestaurantCreateRequest.builder()
                .categoryId(99L)
                .name("새 가게")
                .address("서울시 종로구")
                .build();

        assertThatThrownBy(() -> restaurantService.create(request, new Actor(OWNER_ID, false), "ko"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("update_다른_점주가_수정하면_403이_아니라_RESTAURANT_NOT_FOUND")
    void update_byNonOwner_throwsNotFoundNotForbidden() {
        given(restaurantRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(restaurant));

        RestaurantUpdateRequest request = RestaurantUpdateRequest.builder().name("탈취 시도").build();

        assertThatThrownBy(() ->
                restaurantService.update(1L, request, new Actor(OTHER_OWNER_ID, false), "ko"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);

        assertThat(restaurant.getName()).isEqualTo("서울 김치찌개");
    }

    @Test
    @DisplayName("update_관리자는_소유자가_아니어도_수정할_수_있다")
    void update_byAdmin_isAllowed() {
        given(restaurantRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(restaurant));

        RestaurantUpdateRequest request = RestaurantUpdateRequest.builder().name("관리자 수정").build();

        RestaurantResponse result =
                restaurantService.update(1L, request, new Actor(OTHER_OWNER_ID, true), "ko");

        assertThat(result.name()).isEqualTo("관리자 수정");
    }

    @Test
    @DisplayName("update_원본_이름이_바뀌면_기존_번역을_폐기한다")
    void update_sourceNameChanged_invalidatesTranslations() {
        restaurant.putTranslation("ja", "ソウルキムチチゲ", "30年伝統");
        given(restaurantRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(restaurant));

        RestaurantUpdateRequest request = RestaurantUpdateRequest.builder().name("바뀐 이름").build();
        restaurantService.update(1L, request, new Actor(OWNER_ID, false), "ko");

        assertThat(restaurant.getTranslations()).isEmpty();
        assertThat(restaurant.resolveName("ja")).isEqualTo("바뀐 이름");
    }

    @Test
    @DisplayName("update_원본_텍스트가_그대로면_번역을_유지한다")
    void update_sourceTextUnchanged_keepsTranslations() {
        restaurant.putTranslation("ja", "ソウルキムチチゲ", "30年伝統");
        given(restaurantRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(restaurant));

        RestaurantUpdateRequest request = RestaurantUpdateRequest.builder()
                .name("서울 김치찌개")
                .deliveryFee(5000)
                .build();
        restaurantService.update(1L, request, new Actor(OWNER_ID, false), "ko");

        assertThat(restaurant.getTranslations()).hasSize(1);
        assertThat(restaurant.getDeliveryFee()).isEqualTo(5000);
    }

    @Test
    @DisplayName("delete_소유자가_호출하면_비활성화한다")
    void delete_byOwner_deactivates() {
        given(restaurantRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(restaurant));

        restaurantService.delete(1L, new Actor(OWNER_ID, false));

        assertThat(restaurant.isActive()).isFalse();
    }

    @Test
    @DisplayName("delete_다른_점주가_호출하면_RESTAURANT_NOT_FOUND이고_상태가_유지된다")
    void delete_byNonOwner_throwsNotFound() {
        given(restaurantRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantService.delete(1L, new Actor(OTHER_OWNER_ID, false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);

        assertThat(restaurant.isActive()).isTrue();
    }

    @Test
    @DisplayName("update_존재하지_않는_카테고리로_변경하면_CATEGORY_NOT_FOUND")
    void update_unknownCategory_throwsNotFound() {
        given(restaurantRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(restaurant));
        given(categoryRepository.findById(eq(99L))).willReturn(Optional.empty());

        RestaurantUpdateRequest request = RestaurantUpdateRequest.builder().categoryId(99L).build();

        assertThatThrownBy(() ->
                restaurantService.update(1L, request, new Actor(OWNER_ID, false), "ko"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }
}
