package com.rtcdelivery.payment.repository;

import com.rtcdelivery.payment.domain.OutboxEvent;
import com.rtcdelivery.payment.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            OutboxStatus status,
            LocalDateTime nextRetryAt,
            Pageable pageable
    );
}
