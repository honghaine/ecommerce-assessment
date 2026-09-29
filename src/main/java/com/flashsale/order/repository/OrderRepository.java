package com.flashsale.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashsale.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByUserIdAndIdempotencyKey(long userId, String idempotencyKey);
}
