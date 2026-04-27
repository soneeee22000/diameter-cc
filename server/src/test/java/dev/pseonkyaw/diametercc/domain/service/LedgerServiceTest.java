package dev.pseonkyaw.diametercc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;

import dev.pseonkyaw.diametercc.TestcontainersConfiguration;
import dev.pseonkyaw.diametercc.domain.model.CreditAccount;
import dev.pseonkyaw.diametercc.domain.model.LedgerTransaction;
import dev.pseonkyaw.diametercc.domain.model.LedgerTransaction.Op;
import dev.pseonkyaw.diametercc.domain.repository.CreditAccountRepository;
import dev.pseonkyaw.diametercc.domain.repository.LedgerTransactionRepository;

/**
 * Verifies the atomic primitives of {@link LedgerService} against a real
 * Postgres container. Gated on env ENABLE_DOCKER_TESTS=true.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan("dev.pseonkyaw.diametercc.domain.model")
@EnableJpaRepositories("dev.pseonkyaw.diametercc.domain.repository")
@EnableAutoConfiguration
@Import({TestcontainersConfiguration.class, LedgerServiceTest.LedgerOnlyConfig.class})
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(named = "ENABLE_DOCKER_TESTS", matches = "true",
    disabledReason = "Set ENABLE_DOCKER_TESTS=true to run Docker-based ledger tests")
class LedgerServiceTest {

    @TestConfiguration
    static class LedgerOnlyConfig {
        @Bean
        LedgerService ledgerService(CreditAccountRepository accounts, LedgerTransactionRepository ledger) {
            return new LedgerService(accounts, ledger);
        }
    }

    @Autowired LedgerService ledgerService;
    @Autowired CreditAccountRepository accounts;
    @Autowired LedgerTransactionRepository ledger;

    private static final String MSISDN = "33745146129";

    @BeforeEach
    void resetBalance() {
        CreditAccount a = accounts.findById(MSISDN).orElseThrow();
        if (a.getBalanceUnits() != 600L) {
            a.setBalanceUnits(600L);
            accounts.saveAndFlush(a);
        }
    }

    @Test
    @Rollback
    void reserveDebitsBalanceAndAuditsRow() {
        long granted = ledgerService.reserve(MSISDN, 60L, "sess-A", 0);

        assertThat(granted).isEqualTo(60L);
        assertThat(accounts.findById(MSISDN).orElseThrow().getBalanceUnits()).isEqualTo(540L);

        List<LedgerTransaction> rows = ledger.findBySessionIdOrderByIdAsc("sess-A");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOp()).isEqualTo(Op.RESERVE);
        assertThat(rows.get(0).getUnits()).isEqualTo(60L);
        assertThat(rows.get(0).getBalanceAfter()).isEqualTo(540L);
    }

    @Test
    @Rollback
    void reservePartialWhenBalanceBelowRequest() {
        accounts.findById(MSISDN).ifPresent(a -> {
            a.setBalanceUnits(40L);
            accounts.saveAndFlush(a);
        });

        long granted = ledgerService.reserve(MSISDN, 60L, "sess-B", 0);

        assertThat(granted).isEqualTo(40L);
        assertThat(accounts.findById(MSISDN).orElseThrow().getBalanceUnits()).isZero();
    }

    @Test
    @Rollback
    void reserveReturnsZeroWhenBalanceEmpty() {
        accounts.findById(MSISDN).ifPresent(a -> {
            a.setBalanceUnits(0L);
            accounts.saveAndFlush(a);
        });

        long granted = ledgerService.reserve(MSISDN, 60L, "sess-C", 0);

        assertThat(granted).isZero();
        assertThat(ledger.findBySessionIdOrderByIdAsc("sess-C")).isEmpty();
    }

    @Test
    @Rollback
    void refundCreditsBalanceBack() {
        ledgerService.reserve(MSISDN, 60L, "sess-D", 0);
        assertThat(accounts.findById(MSISDN).orElseThrow().getBalanceUnits()).isEqualTo(540L);

        ledgerService.refund(MSISDN, 30L, "sess-D", 1);

        assertThat(accounts.findById(MSISDN).orElseThrow().getBalanceUnits()).isEqualTo(570L);
        List<LedgerTransaction> rows = ledger.findBySessionIdOrderByIdAsc("sess-D");
        assertThat(rows).extracting(LedgerTransaction::getOp).containsExactly(Op.RESERVE, Op.REFUND);
    }

    @Test
    @Rollback
    void recordDebitEmitsAuditRowWithoutTouchingBalance() {
        ledgerService.reserve(MSISDN, 60L, "sess-E", 0);
        long balanceBefore = accounts.findById(MSISDN).orElseThrow().getBalanceUnits();

        ledgerService.recordDebit(MSISDN, 50L, "sess-E", 1);

        assertThat(accounts.findById(MSISDN).orElseThrow().getBalanceUnits()).isEqualTo(balanceBefore);
        List<LedgerTransaction> rows = ledger.findBySessionIdOrderByIdAsc("sess-E");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getOp()).isEqualTo(Op.DEBIT);
        assertThat(rows.get(1).getUnits()).isEqualTo(50L);
    }
}
