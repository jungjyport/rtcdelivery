package com.rtcdelivery.foodcatalog.repository;

import com.rtcdelivery.foodcatalog.config.JpaAuditingConfig;
import com.rtcdelivery.foodcatalog.domain.Category;
import com.rtcdelivery.foodcatalog.domain.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색 JPQL의 동작 검증. 번역명 검색을 컬렉션 join 대신 EXISTS 서브쿼리로 쓴 이유가
 * 페이지네이션 정확성이므로, 총 건수까지 함께 확인한다.
 *
 * DB는 @DataJpaTest가 클래스패스의 H2를 임베디드로 꽂아준다. 다만 application.properties의
 * MySQL 전제 설정이 그대로 딸려오므로, 이 클래스에서만 세 가지를 덮는다.
 * 이 프로젝트에서 DB가 필요한 테스트는 여기뿐이라 별도 프로파일을 두지 않았다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        // MySQLDialect로는 H2에 스키마가 생성되지 않는다.
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // 운영 기본값 update 대신, 매번 깨끗한 스키마에서 시작한다.
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // data.sql은 MySQL 전용 INSERT IGNORE 구문이라 H2에서 실패한다.
        "spring.sql.init.mode=never"
})
class RestaurantRepositoryTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 10);

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category korean;
    private Category japanese;

    @BeforeEach
    void setUp() {
        korean = categoryRepository.save(
                Category.builder().code("korean").name("한식").displayOrder(1).isActive(true).build());
        japanese = categoryRepository.save(
                Category.builder().code("japanese").name("일식").displayOrder(2).isActive(true).build());

        Restaurant kimchi = Restaurant.builder()
                .ownerId(100L).category(korean).name("서울 김치찌개")
                .address("서울시 강남구").isActive(true).build();
        kimchi.putTranslation("ja", "ソウルキムチチゲ", "30年伝統");

        Restaurant sushi = Restaurant.builder()
                .ownerId(100L).category(japanese).name("스시 오마카세")
                .address("서울시 강남구").isActive(true).build();

        Restaurant closed = Restaurant.builder()
                .ownerId(200L).category(korean).name("폐업한 김치집")
                .address("서울시 종로구").isActive(false).build();

        restaurantRepository.save(kimchi);
        restaurantRepository.save(sushi);
        restaurantRepository.save(closed);
    }

    @Test
    @DisplayName("search_조건이_없으면_활성_음식점만_반환한다")
    void search_noFilter_returnsActiveOnly() {
        Page<Restaurant> result = restaurantRepository.search(null, null, "ko", FIRST_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Restaurant::getName)
                .containsExactlyInAnyOrder("서울 김치찌개", "스시 오마카세");
    }

    @Test
    @DisplayName("search_카테고리로_필터링한다")
    void search_byCategory_filtersResults() {
        Page<Restaurant> result = restaurantRepository.search(japanese.getId(), null, "ko", FIRST_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("스시 오마카세");
    }

    @Test
    @DisplayName("search_원본명_키워드로_찾는다")
    void search_bySourceName_matches() {
        Page<Restaurant> result = restaurantRepository.search(null, "김치", "ko", FIRST_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("서울 김치찌개");
    }

    @Test
    @DisplayName("search_요청_locale의_번역명으로도_찾는다")
    void search_byTranslatedName_matches() {
        Page<Restaurant> result = restaurantRepository.search(null, "キムチ", "ja", FIRST_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("서울 김치찌개");
    }

    @Test
    @DisplayName("search_다른_locale로_요청하면_그_locale의_번역만_검색한다")
    void search_otherLocale_doesNotMatchJapaneseTranslation() {
        Page<Restaurant> result = restaurantRepository.search(null, "キムチ", "ko", FIRST_PAGE);

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("search_비활성_음식점은_키워드가_맞아도_제외한다")
    void search_inactiveRestaurant_isExcluded() {
        Page<Restaurant> result = restaurantRepository.search(null, "폐업", "ko", FIRST_PAGE);

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("search_번역이_여러_건_있어도_결과가_중복되지_않는다")
    void search_multipleTranslations_doesNotDuplicateRows() {
        Restaurant multi = Restaurant.builder()
                .ownerId(100L).category(korean).name("다국어 식당")
                .address("서울시 서초구").isActive(true).build();
        multi.putTranslation("ja", "多言語食堂", null);
        multi.putTranslation("en", "Multilingual Diner", null);
        restaurantRepository.save(multi);

        Page<Restaurant> result = restaurantRepository.search(null, "다국어", "ja", FIRST_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("findByIdAndIsActiveTrue_비활성_음식점은_찾지_못한다")
    void findByIdAndIsActiveTrue_inactive_returnsEmpty() {
        Restaurant inactive = restaurantRepository.save(Restaurant.builder()
                .ownerId(300L).category(korean).name("임시 휴업")
                .address("서울시 은평구").isActive(false).build());

        assertThat(restaurantRepository.findByIdAndIsActiveTrue(inactive.getId())).isEmpty();
    }
}
