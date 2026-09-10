package com.rtcdelivery.foodcatalog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FoodTest {

    private static Food food() {
        Restaurant restaurant = Restaurant.builder()
                .id(1L)
                .ownerId(100L)
                .category(Category.builder().id(1L).code("korean").name("한식").build())
                .name("서울 김치찌개")
                .address("서울시 강남구")
                .build();

        return Food.builder()
                .id(10L)
                .restaurant(restaurant)
                .name("김치찌개")
                .description("돼지고기와 묵은지")
                .price(9000)
                .build();
    }

    @Test
    @DisplayName("resolveName_번역이_없으면_원본을_반환한다")
    void resolveName_noTranslation_returnsSourceText() {
        assertThat(food().resolveName("ja")).isEqualTo("김치찌개");
    }

    @Test
    @DisplayName("resolveName_해당_locale_번역이_있으면_번역본을_반환한다")
    void resolveName_withTranslation_returnsTranslated() {
        Food food = food();
        food.putTranslation("ja", "キムチチゲ", "豚肉と熟成キムチ");

        assertThat(food.resolveName("ja")).isEqualTo("キムチチゲ");
        assertThat(food.resolveDescription("ja")).isEqualTo("豚肉と熟成キムチ");
    }

    @Test
    @DisplayName("clearTranslations_모든_번역을_지우면_원본으로_폴백한다")
    void clearTranslations_removesAll_fallsBackToSource() {
        Food food = food();
        food.putTranslation("ja", "キムチチゲ", "豚肉と熟成キムチ");

        food.clearTranslations();

        assertThat(food.getTranslations()).isEmpty();
        assertThat(food.resolveName("ja")).isEqualTo("김치찌개");
    }

    @Test
    @DisplayName("update_null_필드는_기존_값을_유지한다")
    void update_nullFields_keepExistingValues() {
        Food food = food();

        food.update(null, null, 12000, null, 3);

        assertThat(food.getName()).isEqualTo("김치찌개");
        assertThat(food.getPrice()).isEqualTo(12000);
        assertThat(food.getDisplayOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("changeSoldOut_품절_상태를_바꾼다")
    void changeSoldOut_togglesFlag() {
        Food food = food();

        food.changeSoldOut(true);
        assertThat(food.isSoldOut()).isTrue();

        food.changeSoldOut(false);
        assertThat(food.isSoldOut()).isFalse();
    }
}
