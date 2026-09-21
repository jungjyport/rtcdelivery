package com.rtcdelivery.translation.repository;

import com.rtcdelivery.translation.domain.JobStatus;
import com.rtcdelivery.translation.domain.TranslationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TranslationJobRepository extends JpaRepository<TranslationJob, Long> {

    @Query(value = """
            SELECT * FROM translation_job
            WHERE status = 'PENDING' AND next_retry_at <= :now
            ORDER BY next_retry_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<TranslationJob> lockPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE TranslationJob j
            SET j.status = 'SUPERSEDED'
            WHERE j.restaurantId = :restaurantId
              AND j.targetLocale = :targetLocale
              AND j.status = 'PENDING'
            """)
    int supersedePendingJobs(@Param("restaurantId") Long restaurantId, @Param("targetLocale") String targetLocale);

    List<TranslationJob> findByRestaurantIdAndTargetLocale(Long restaurantId, String targetLocale);
}
