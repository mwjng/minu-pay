package com.minupay.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    List<Outbox> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
