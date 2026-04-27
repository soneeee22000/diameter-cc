package dev.pseonkyaw.diametercc.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.pseonkyaw.diametercc.domain.model.CreditAccount;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, String> {
}
