package com.rtcdelivery.translation.repository;

import com.rtcdelivery.translation.domain.UgcContentType;
import com.rtcdelivery.translation.domain.UgcTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UgcTranslationRepository extends JpaRepository<UgcTranslation, Long> {

    Optional<UgcTranslation> findByContentTypeAndContentIdAndSourceHashAndTargetLocale(
            UgcContentType contentType, Long contentId, String sourceHash, String targetLocale);
}
