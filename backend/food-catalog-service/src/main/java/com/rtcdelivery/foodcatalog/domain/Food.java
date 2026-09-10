package com.rtcdelivery.foodcatalog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.HashMap;
import java.util.Map;

/**
 * 메뉴. 소유권은 자체적으로 갖지 않고 소속 {@link Restaurant}의 {@code ownerId}를 따른다.
 */
@Entity
@Table(
        name = "foods",
        indexes = {
                @Index(name = "idx_foods_restaurant", columnList = "restaurant_id")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Food extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    /**
     * 원본(ko) 이름. 번역이 없는 locale은 이 값으로 폴백한다.
     */
    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int price;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Builder.Default
    @Column(name = "is_sold_out", nullable = false)
    private boolean isSoldOut = false;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /**
     * {@link Restaurant#translations}와 동일한 이유로 배치 로딩을 쓴다.
     */
    @Builder.Default
    @OneToMany(mappedBy = "food", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @MapKey(name = "locale")
    @BatchSize(size = 100)
    private Map<String, FoodTranslation> translations = new HashMap<>();

    public void update(String name,
                       String description,
                       Integer price,
                       String imageUrl,
                       Integer displayOrder) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null) {
            this.price = price;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }

    public void changeSoldOut(boolean soldOut) {
        this.isSoldOut = soldOut;
    }

    public void putTranslation(String locale, String name, String description) {
        FoodTranslation existing = this.translations.get(locale);
        if (existing != null) {
            existing.update(name, description);
            return;
        }
        this.translations.put(locale, FoodTranslation.builder()
                .food(this)
                .locale(locale)
                .name(name)
                .description(description)
                .build());
    }

    public void clearTranslations() {
        this.translations.clear();
    }

    public String resolveName(String locale) {
        FoodTranslation translation = this.translations.get(locale);
        return (translation != null && translation.getName() != null) ? translation.getName() : this.name;
    }

    public String resolveDescription(String locale) {
        FoodTranslation translation = this.translations.get(locale);
        return (translation != null && translation.getDescription() != null)
                ? translation.getDescription()
                : this.description;
    }
}
