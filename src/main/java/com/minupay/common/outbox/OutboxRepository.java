package com.minupay.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    @Query("SELECT o FROM Outbox o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC LIMIT :limit")
    List<Outbox> findPendingEvents(int limit);
}
