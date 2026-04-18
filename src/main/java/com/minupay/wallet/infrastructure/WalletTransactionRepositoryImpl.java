package com.minupay.wallet.infrastructure;

import com.minupay.wallet.domain.WalletTransaction;
import com.minupay.wallet.domain.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WalletTransactionRepositoryImpl implements WalletTransactionRepository {

    private final WalletTransactionJpaRepository jpaRepository;

    @Override
    public WalletTransaction save(WalletTransaction transaction) {
        return jpaRepository.save(WalletTransactionEntity.from(transaction)).toDomain();
    }
}
