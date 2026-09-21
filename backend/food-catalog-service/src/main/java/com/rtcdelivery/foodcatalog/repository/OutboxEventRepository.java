package com.rtcdelivery.foodcatalog.repository;

import com.rtcdelivery.foodcatalog.domain.OutboxEvent;
import com.rtcdelivery.foodcatalog.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(String eventId);

    List<OutboxEvent> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            OutboxStatus status, LocalDateTime nextRetryAt, Pageable pageable);
}
