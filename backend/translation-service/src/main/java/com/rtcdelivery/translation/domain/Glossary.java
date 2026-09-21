package com.rtcdelivery.translation.domain;

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

@Entity
@Table(
        name = "glossary",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_glossary", columnNames = {"source_text", "source_locale", "target_locale"})
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Glossary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_text", nullable = false, length = 255)
    private String sourceText;

    @Column(name = "source_locale", nullable = false, length = 10)
    private String sourceLocale;

    @Column(name = "target_locale", nullable = false, length = 10)
    private String targetLocale;

    @Column(name = "translated_text", nullable = false, length = 255)
    private String translatedText;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;
}
