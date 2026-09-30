package com.flashsale.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashsale.inventory.entity.InventorySyncLog;

public interface InventorySyncLogRepository extends JpaRepository<InventorySyncLog, Long> {

    Optional<InventorySyncLog> findBySourceAndExternalEventId(String source, String externalEventId);
}
