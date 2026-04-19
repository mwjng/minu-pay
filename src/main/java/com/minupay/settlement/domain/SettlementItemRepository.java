package com.minupay.settlement.domain;

import java.util.Optional;

public interface SettlementItemRepository {
    SettlementItem save(SettlementItem item);
    Optional<SettlementItem> findByPaymentId(String paymentId);
}
