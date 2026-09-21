package com.rtcdelivery.translation.repository;

import com.rtcdelivery.translation.domain.Glossary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GlossaryRepository extends JpaRepository<Glossary, Long> {

    Optional<Glossary> findBySourceTextAndSourceLocaleAndTargetLocaleAndIsActiveTrue(
            String sourceText, String sourceLocale, String targetLocale);
}
