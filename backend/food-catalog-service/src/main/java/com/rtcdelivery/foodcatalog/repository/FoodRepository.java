package com.rtcdelivery.foodcatalog.repository;

import com.rtcdelivery.foodcatalog.domain.Food;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {

    List<Food> findAllByRestaurantIdOrderByDisplayOrderAscIdAsc(Long restaurantId);

    Optional<Food> findByIdAndRestaurantId(Long id, Long restaurantId);

    /**
     * 메뉴 검색. 조건 구성 이유는 {@link RestaurantRepository#search}와 같다.
     */
    @Query("""
            SELECT f FROM Food f
            WHERE f.restaurant.isActive = true
              AND (:restaurantId IS NULL OR f.restaurant.id = :restaurantId)
              AND (:keyword IS NULL
                   OR LOWER(f.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR EXISTS (SELECT 1 FROM FoodTranslation ft
                              WHERE ft.food = f
                                AND ft.locale = :locale
                                AND LOWER(ft.name) LIKE LOWER(CONCAT('%', :keyword, '%'))))
            """)
    Page<Food> search(@Param("restaurantId") Long restaurantId,
                      @Param("keyword") String keyword,
                      @Param("locale") String locale,
                      Pageable pageable);
}
