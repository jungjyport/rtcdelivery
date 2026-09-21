package com.rtcdelivery.translation.repository;

import com.rtcdelivery.translation.domain.TranslationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TranslationHistoryRepository extends JpaRepository<TranslationHistory, Long> {

    Optional<TranslationHistory> findBySourceHashAndSourceLocaleAndTargetLocale(
            String sourceHash, String sourceLocale, String targetLocale);
}
