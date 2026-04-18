package com.minupay.wallet.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface WalletTransactionJpaRepository extends JpaRepository<WalletTransactionEntity, Long> {}
