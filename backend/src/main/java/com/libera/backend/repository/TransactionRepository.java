package com.libera.backend.repository;

import com.libera.backend.domain.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    boolean existsByResalePurchaseId(Long resalePurchaseId);
}
