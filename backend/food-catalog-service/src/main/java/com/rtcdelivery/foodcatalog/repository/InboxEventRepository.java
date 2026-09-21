package com.rtcdelivery.foodcatalog.repository;

import com.rtcdelivery.foodcatalog.domain.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

    Optional<InboxEvent> findByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
