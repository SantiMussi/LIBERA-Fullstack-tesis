package com.libera.backend.repository;

import com.libera.backend.domain.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    boolean existsByResalePurchaseId(Long resalePurchaseId);

    Optional<Transaction> findByResalePurchaseId(Long resalePurchaseId);

    List<Transaction> findByResalePurchaseIdIn(Collection<Long> resalePurchaseIds);
}
