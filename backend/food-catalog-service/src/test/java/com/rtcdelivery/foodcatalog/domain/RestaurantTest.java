package com.rtcdelivery.foodcatalog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantTest {

    private static Category category() {
        return Category.builder().id(1L).code("korean").name("한식").build();
    }

    private static Restaurant restaurant() {
        return Restaurant.builder()
                .id(1L)
                .ownerId(100L)
                .category(category())
                .name("서울 김치찌개")
                .description("30년 전통")
                .address("서울시 강남구")
                .build();
    }

    @Test
    @DisplayName("resolveName_번역이_없으면_원본을_반환한다")
    void resolveName_noTranslation_returnsSourceText() {
        Restaurant restaurant = restaurant();

        assertThat(restaurant.resolveName("ja")).isEqualTo("서울 김치찌개");
        assertThat(restaurant.resolveDescription("ja")).isEqualTo("30년 전통");
    }

    @Test
    @DisplayName("resolveName_해당_locale_번역이_있으면_번역본을_반환한다")
    void resolveName_withTranslation_returnsTranslated() {
        Restaurant restaurant = restaurant();
        restaurant.putTranslation("ja", "ソウルキムチチゲ", "30年伝統");

        assertThat(restaurant.resolveName("ja")).isEqualTo("ソウルキムチチゲ");
        assertThat(restaurant.resolveDescription("ja")).isEqualTo("30年伝統");
    }

    @Test
    @DisplayName("resolveName_다른_locale_번역만_있으면_원본으로_폴백한다")
    void resolveName_otherLocaleOnly_fallsBackToSource() {
        Restaurant restaurant = restaurant();
        restaurant.putTranslation("ja", "ソウルキムチチゲ", "30年伝統");

        assertThat(restaurant.resolveName("en")).isEqualTo("서울 김치찌개");
    }

    @Test
    @DisplayName("putTranslation_같은_locale로_다시_넣으면_새_행을_만들지_않고_덮어쓴다")
    void putTranslation_sameLocaleTwice_overwritesInPlace() {
        Restaurant restaurant = restaurant();
        restaurant.putTranslation("ja", "旧名", "旧説明");
        restaurant.putTranslation("ja", "新名", "新説明");

        assertThat(restaurant.getTranslations()).hasSize(1);
        assertThat(restaurant.resolveName("ja")).isEqualTo("新名");
        assertThat(restaurant.resolveDescription("ja")).isEqualTo("新説明");
    }

    @Test
    @DisplayName("clearTranslations_모든_번역을_지우면_원본으로_폴백한다")
    void clearTranslations_removesAll_fallsBackToSource() {
        Restaurant restaurant = restaurant();
        restaurant.putTranslation("ja", "ソウルキムチチゲ", "30年伝統");

        restaurant.clearTranslations();

        assertThat(restaurant.getTranslations()).isEmpty();
        assertThat(restaurant.resolveName("ja")).isEqualTo("서울 김치찌개");
    }

    @Test
    @DisplayName("isOwnedBy_소유자_ID가_일치하면_true")
    void isOwnedBy_matchingId_returnsTrue() {
        assertThat(restaurant().isOwnedBy(100L)).isTrue();
    }

    @Test
    @DisplayName("isOwnedBy_다른_ID나_null이면_false")
    void isOwnedBy_otherOrNullId_returnsFalse() {
        Restaurant restaurant = restaurant();

        assertThat(restaurant.isOwnedBy(999L)).isFalse();
        assertThat(restaurant.isOwnedBy(null)).isFalse();
    }

    @Test
    @DisplayName("update_null_필드는_기존_값을_유지한다")
    void update_nullFields_keepExistingValues() {
        Restaurant restaurant = restaurant();

        restaurant.update(null, "새 이름", null, null, null, 5000, null, null);

        assertThat(restaurant.getName()).isEqualTo("새 이름");
        assertThat(restaurant.getDescription()).isEqualTo("30년 전통");
        assertThat(restaurant.getAddress()).isEqualTo("서울시 강남구");
        assertThat(restaurant.getDeliveryFee()).isEqualTo(5000);
    }

    @Test
    @DisplayName("deactivate_비활성_상태로_바꾼다")
    void deactivate_marksInactive() {
        Restaurant restaurant = Restaurant.builder()
                .ownerId(100L)
                .category(category())
                .name("가게")
                .address("주소")
                .isActive(true)
                .build();

        restaurant.deactivate();

        assertThat(restaurant.isActive()).isFalse();
    }
}
