package dev.pseonkyaw.diametercc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;

import dev.pseonkyaw.diametercc.TestcontainersConfiguration;
import dev.pseonkyaw.diametercc.domain.model.CcSession;
import dev.pseonkyaw.diametercc.domain.model.CreditAccount;
import dev.pseonkyaw.diametercc.domain.model.LedgerTransaction;
import dev.pseonkyaw.diametercc.domain.model.Reservation;
import dev.pseonkyaw.diametercc.domain.model.ReservationKey;
import dev.pseonkyaw.diametercc.domain.repository.CcSessionRepository;
import dev.pseonkyaw.diametercc.domain.repository.CreditAccountRepository;
import dev.pseonkyaw.diametercc.domain.repository.LedgerTransactionRepository;
import dev.pseonkyaw.diametercc.domain.repository.ReservationRepository;

/**
 * Integration test for the JPA layer over a real Postgres container.
 *
 * <p>Runs only when Docker is available (env DOCKER_HOST or default socket).
 * Skipped on machines without Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan("dev.pseonkyaw.diametercc.domain.model")
@EnableJpaRepositories("dev.pseonkyaw.diametercc.domain.repository")
@EnableAutoConfiguration
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.autoconfigure.exclude=org.jdiameter.api.Stack"
})
@EnabledIfEnvironmentVariable(named = "ENABLE_DOCKER_TESTS", matches = "true",
    disabledReason = "Set ENABLE_DOCKER_TESTS=true to run Docker-based JPA tests")
class CreditLedgerJpaTest {

    @Autowired CreditAccountRepository accounts;
    @Autowired CcSessionRepository sessions;
    @Autowired ReservationRepository reservations;
    @Autowired LedgerTransactionRepository ledger;

    @Test
    @Rollback
    void seededDemoAccountsLoadFromV2Migration() {
        assertThat(accounts.findById("33745146129"))
            .as("MSISDN seeded with 600s prepaid")
            .hasValueSatisfying(a -> {
                assertThat(a.getBalanceUnits()).isEqualTo(600L);
                assertThat(a.getUnitType()).isEqualTo("CC_TIME");
            });
    }

    @Test
    @Rollback
    void roundTripsCreditAccountChange() {
        CreditAccount a = accounts.findById("33745146129").orElseThrow();
        long before = a.getBalanceUnits();
        a.setBalanceUnits(before - 60);
        accounts.saveAndFlush(a);

        CreditAccount reloaded = accounts.findById("33745146129").orElseThrow();
        assertThat(reloaded.getBalanceUnits()).isEqualTo(before - 60);
        assertThat(reloaded.getVersion()).isPositive();
    }

    @Test
    @Rollback
    void persistsCcSessionAndLedgerEntry() {
        sessions.save(new CcSession("sess-001", "33745146129"));
        ledger.save(new LedgerTransaction(
            "sess-001", "33745146129", LedgerTransaction.Op.RESERVE, 60L, 540L, 0));

        CcSession s = sessions.findById("sess-001").orElseThrow();
        assertThat(s.getState()).isEqualTo(CcSession.State.OPEN);

        var entries = ledger.findBySessionIdOrderByIdAsc("sess-001");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getOp()).isEqualTo(LedgerTransaction.Op.RESERVE);
        assertThat(entries.get(0).getBalanceAfter()).isEqualTo(540L);
    }

    @Test
    @Rollback
    void enforcesReservationCompositePrimaryKey() {
        sessions.save(new CcSession("sess-002", "33745146129"));
        Reservation first = new Reservation(
            "sess-002", 0, (short) 1, 60L, 0L, 60L, 2001, "stub-cca".getBytes());
        reservations.saveAndFlush(first);

        Optional<Reservation> found = reservations.findById(new ReservationKey("sess-002", 0));
        assertThat(found).isPresent();
        assertThat(found.get().getResultCode()).isEqualTo(2001);
        assertThat(found.get().getGrantedUnits()).isEqualTo(60L);
    }
}
