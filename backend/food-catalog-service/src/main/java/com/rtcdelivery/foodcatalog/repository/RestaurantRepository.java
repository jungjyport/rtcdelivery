package com.rtcdelivery.foodcatalog.repository;

import com.rtcdelivery.foodcatalog.domain.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Optional<Restaurant> findByIdAndIsActiveTrue(Long id);

    /**
     * 활성 음식점 목록. 카테고리와 키워드는 모두 선택 조건이다.
     *
     * <p>번역 이름 검색에 컬렉션 join 대신 {@code EXISTS} 서브쿼리를 쓴다. join을 쓰면
     * 번역 행 수만큼 중복이 생겨 {@code DISTINCT}가 필요해지고, 그러면 Spring Data가
     * 파생하는 count 쿼리와 페이지네이션이 어긋난다.
     */
    @Query("""
            SELECT r FROM Restaurant r
            WHERE r.isActive = true
              AND (:categoryId IS NULL OR r.category.id = :categoryId)
              AND (:keyword IS NULL
                   OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR EXISTS (SELECT 1 FROM RestaurantTranslation rt
                              WHERE rt.restaurant = r
                                AND rt.locale = :locale
                                AND LOWER(rt.name) LIKE LOWER(CONCAT('%', :keyword, '%'))))
            """)
    Page<Restaurant> search(@Param("categoryId") Long categoryId,
                            @Param("keyword") String keyword,
                            @Param("locale") String locale,
                            Pageable pageable);
}
