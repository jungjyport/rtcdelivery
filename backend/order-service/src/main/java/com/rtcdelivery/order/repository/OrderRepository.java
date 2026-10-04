package com.rtcdelivery.order.repository;

import com.rtcdelivery.order.domain.FulfillmentStatus;
import com.rtcdelivery.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    Page<Order> findByMemberId(Long memberId, Pageable pageable);

    @Query("""
            SELECT o FROM Order o
            WHERE (:ownerId IS NULL OR o.restaurantOwnerId = :ownerId)
              AND (:restaurantId IS NULL OR o.restaurantId = :restaurantId)
              AND (:status IS NULL OR o.fulfillmentStatus = :status)
            """)
    Page<Order> searchManaged(@Param("ownerId") Long ownerId,
                              @Param("restaurantId") Long restaurantId,
                              @Param("status") FulfillmentStatus status,
                              Pageable pageable);
}
