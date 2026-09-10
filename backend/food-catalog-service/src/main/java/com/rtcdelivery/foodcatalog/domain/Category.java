package com.rtcdelivery.foodcatalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 음식 카테고리.
 *
 * <p>번역 테이블을 두지 않는다. 카테고리는 개수가 적고 고정적이라 프론트엔드 i18n
 * (`category.{code}` 키)이 담당하는 정적 UI 텍스트로 분류된다.
 * 근거는 docs/internationalization.md §2-A / §4 참조.
 *
 * <p>따라서 {@code code}는 프론트 번역 키와 1:1로 대응하는 계약이다. 한번 배포된 code를
 * 바꾸면 프론트에서 키를 찾지 못해 원문이 그대로 노출된다.
 */
@Entity
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_categories_code", columnNames = {"code"})
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Category extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 프론트엔드 i18n 키 접미사. 예: {@code korean} → {@code $t('category.korean')}
     */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * 관리용 한국어 이름. 사용자 노출에는 쓰지 않는다 (프론트가 i18n으로 표출).
     */
    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public void update(String name, String imageUrl, Integer displayOrder) {
        if (name != null) {
            this.name = name;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}
