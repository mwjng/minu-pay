package com.minupay.wallet.infrastructure;

import com.minupay.wallet.domain.Wallet;
import com.minupay.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WalletRepositoryImpl implements WalletRepository {

    private final WalletJpaRepository jpaRepository;

    @Override
    public Wallet save(Wallet wallet) {
        return jpaRepository.save(WalletEntity.from(wallet)).toDomain();
    }

    @Override
    public Optional<Wallet> findById(Long id) {
        return jpaRepository.findById(id).map(WalletEntity::toDomain);
    }

    @Override
    public Optional<Wallet> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).map(WalletEntity::toDomain);
    }

    @Override
    public Optional<Wallet> findByUserIdWithLock(Long userId) {
        return jpaRepository.findByUserIdWithLock(userId).map(WalletEntity::toDomain);
    }
}
