package com.rtcdelivery.foodcatalog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

@Entity
@Table(
        name = "restaurants",
        indexes = {
                @jakarta.persistence.Index(name = "idx_restaurants_owner", columnList = "owner_id"),
                @jakarta.persistence.Index(name = "idx_restaurants_category", columnList = "category_id")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Restaurant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소유 점주의 {@code members.id}. member-auth-service가 소유한 값이므로 FK를 걸지 않는다
     * (Database-per-Service). 수정·삭제 시 이 값과 {@code X-User-Id}를 비교해 인가를 판정한다.
     */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * 원본(ko) 이름. 번역이 없는 locale은 이 값으로 폴백한다.
     */
    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Builder.Default
    @Column(name = "delivery_fee", nullable = false)
    private int deliveryFee = 0;

    @Builder.Default
    @Column(name = "min_order_amount", nullable = false)
    private int minOrderAmount = 0;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /**
     * locale을 키로 갖는 번역 모음. 이 테이블은 food-catalog가 소유하며,
     * translation-service가 발행한 {@code translation-results}를 소비해 갱신한다
     * (docs/architecture.md §8.1).
     *
     * <p>{@code @BatchSize}는 목록 조회의 N+1을 막는다. 컬렉션에 fetch join을 걸면
     * Hibernate가 페이지네이션을 메모리에서 처리하므로 그 대신 IN 절 배치 로딩을 쓴다.
     */
    @Builder.Default
    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @MapKey(name = "locale")
    @BatchSize(size = 100)
    private Map<String, RestaurantTranslation> translations = new HashMap<>();

    public void update(Category category,
                       String name,
                       String description,
                       String address,
                       String phoneNumber,
                       Integer deliveryFee,
                       Integer minOrderAmount,
                       String imageUrl) {
        if (category != null) {
            this.category = category;
        }
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (address != null) {
            this.address = address;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        if (deliveryFee != null) {
            this.deliveryFee = deliveryFee;
        }
        if (minOrderAmount != null) {
            this.minOrderAmount = minOrderAmount;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
    }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean isOwnedBy(Long memberId) {
        return memberId != null && memberId.equals(this.ownerId);
    }

    /**
     * 번역을 덮어쓴다. translation-service의 결과를 반영할 때 사용한다.
     */
    public void putTranslation(String locale, String name, String description) {
        RestaurantTranslation existing = this.translations.get(locale);
        if (existing != null) {
            existing.update(name, description);
            return;
        }
        this.translations.put(locale, RestaurantTranslation.builder()
                .restaurant(this)
                .locale(locale)
                .name(name)
                .description(description)
                .build());
    }

    /**
     * 원본 텍스트가 바뀌면 기존 번역은 옛 내용을 가리키게 되므로 모두 버린다.
     * 재번역 결과가 도착하기 전까지는 원본(ko)으로 폴백된다.
     */
    public void clearTranslations() {
        this.translations.clear();
    }

    public String resolveName(String locale) {
        RestaurantTranslation translation = this.translations.get(locale);
        return (translation != null && translation.getName() != null) ? translation.getName() : this.name;
    }

    public String resolveDescription(String locale) {
        RestaurantTranslation translation = this.translations.get(locale);
        return (translation != null && translation.getDescription() != null)
                ? translation.getDescription()
                : this.description;
    }
}
