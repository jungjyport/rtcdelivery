package com.rtcdelivery.foodcatalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메뉴의 locale별 번역. 소유 주체는 {@link RestaurantTranslation}과 같다.
 *
 * <p>테이블명은 docs/translation-system.md §5의 {@code menu_translation}을 따른다.
 * 엔티티명({@code Food})과 다른 이유는 스펙 문서가 도메인 용어로 "menu"를 쓰기 때문이다.
 */
@Entity
@Table(
        name = "menu_translation",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_menu_locale", columnNames = {"menu_id", "locale"})
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FoodTranslation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", nullable = false)
    private Food food;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
