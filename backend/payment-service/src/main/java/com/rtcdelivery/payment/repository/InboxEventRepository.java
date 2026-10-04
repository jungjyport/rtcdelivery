package com.rtcdelivery.payment.repository;

import com.rtcdelivery.payment.domain.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

    boolean existsByEventId(String eventId);
}
