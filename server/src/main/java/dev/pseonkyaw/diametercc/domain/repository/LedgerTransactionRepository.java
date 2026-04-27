package dev.pseonkyaw.diametercc.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.pseonkyaw.diametercc.domain.model.LedgerTransaction;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    List<LedgerTransaction> findBySessionIdOrderByIdAsc(String sessionId);

    List<LedgerTransaction> findByMsisdnOrderByIdDesc(String msisdn);
}
