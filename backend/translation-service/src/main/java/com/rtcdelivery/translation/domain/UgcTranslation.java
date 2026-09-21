package com.rtcdelivery.translation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(
        name = "ugc_translation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ugc",
                        columnNames = {"content_type", "content_id", "source_hash", "target_locale"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UgcTranslation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 30)
    private UgcContentType contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "source_locale", nullable = false, length = 10)
    private String sourceLocale;

    @Column(name = "target_locale", nullable = false, length = 10)
    private String targetLocale;

    @Column(name = "translated_text", nullable = false, columnDefinition = "TEXT")
    private String translatedText;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(length = 100)
    private String model;
}
