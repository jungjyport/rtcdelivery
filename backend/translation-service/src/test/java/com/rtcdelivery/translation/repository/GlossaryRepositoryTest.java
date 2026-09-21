package com.rtcdelivery.translation.repository;

import com.rtcdelivery.translation.config.JpaAuditingConfig;
import com.rtcdelivery.translation.domain.Glossary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class GlossaryRepositoryTest {

    @Autowired
    private GlossaryRepository glossaryRepository;

    @Test
    @DisplayName("사전 완전일치 조회: 활성화된 사전 항목만 조회된다")
    void findActiveGlossary() {
        Glossary active = Glossary.builder()
                .sourceText("김치찌개")
                .sourceLocale("ko")
                .targetLocale("ja")
                .translatedText("キムチチゲ")
                .isActive(true)
                .build();
        glossaryRepository.save(active);

        Glossary inactive = Glossary.builder()
                .sourceText("된장찌개")
                .sourceLocale("ko")
                .targetLocale("ja")
                .translatedText("テンジャンチゲ")
                .isActive(false)
                .build();
        glossaryRepository.save(inactive);

        Optional<Glossary> found = glossaryRepository
                .findBySourceTextAndSourceLocaleAndTargetLocaleAndIsActiveTrue("김치찌개", "ko", "ja");
        assertThat(found).isPresent();
        assertThat(found.get().getTranslatedText()).isEqualTo("キムチチゲ");

        Optional<Glossary> notFound = glossaryRepository
                .findBySourceTextAndSourceLocaleAndTargetLocaleAndIsActiveTrue("된장찌개", "ko", "ja");
        assertThat(notFound).isEmpty();
    }
}
