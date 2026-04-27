package dev.pseonkyaw.diametercc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.Avp;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.DisconnectCause;
import org.jdiameter.api.EventListener;
import org.jdiameter.api.Network;
import org.jdiameter.api.PeerState;
import org.jdiameter.api.PeerTable;
import org.jdiameter.api.Request;
import org.jdiameter.api.Session;
import org.jdiameter.api.SessionFactory;
import org.jdiameter.api.Stack;
import org.jdiameter.client.api.IMessage;
import org.jdiameter.server.impl.StackImpl;
import org.jdiameter.server.impl.helpers.XMLConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import dev.pseonkyaw.diametercc.domain.repository.CreditAccountRepository;
import dev.pseonkyaw.diametercc.domain.repository.LedgerTransactionRepository;
import dev.pseonkyaw.diametercc.gy.AvpCodes;

/**
 * Signature test of the project — proves that a duplicated CCR (same
 * Session-Id, same CC-Request-Number) is detected by the
 * {@code (session_id, cc_request_number)} composite primary key on the
 * Reservation table, and the second hit returns the cached answer
 * without re-debiting the balance or re-emitting ledger rows.
 *
 * <p>This is the same idempotency discipline that cdr-pipeline applies
 * via {@code event_id} — written so the two repos read as a pair.
 *
 * <p>Gated on env ENABLE_DOCKER_TESTS=true.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "diameter.listen-port=3868"
})
@EnabledIfEnvironmentVariable(named = "ENABLE_DOCKER_TESTS", matches = "true",
    disabledReason = "Set ENABLE_DOCKER_TESTS=true to run Docker-based replay tests")
class IdempotencyReplayIntegrationTest {

    private static final long APP_ID_GY = 4L;
    private static final String DEMO_MSISDN = "33799887766"; // 1800s seeded

    @Autowired CreditAccountRepository accounts;
    @Autowired LedgerTransactionRepository ledger;

    private Stack peerStack;
    private SessionFactory peerSessionFactory;

    @BeforeEach
    void resetBalance() {
        accounts.findById(DEMO_MSISDN).ifPresent(a -> {
            if (a.getBalanceUnits() != 1800L) {
                a.setBalanceUnits(1800L);
                accounts.saveAndFlush(a);
            }
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (peerStack != null) {
            peerStack.stop(0L, TimeUnit.MILLISECONDS, DisconnectCause.REBOOTING);
            peerStack.destroy();
        }
    }

    @Test
    @DisplayName("Replayed CCR-Initial debits balance once and returns identical CCAs")
    void duplicateCcrInitialDebitsOnceReturnsIdentical() throws Exception {
        peerStack = bootPeerStack();
        Network peerNetwork = peerStack.unwrap(Network.class);
        ApplicationId gy = ApplicationId.createByAuthAppId(APP_ID_GY);
        peerNetwork.addNetworkReqListener(req -> null, gy);
        peerStack.start();
        peerSessionFactory = peerStack.getSessionFactory();

        await().atMost(15, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            PeerTable pt = peerStack.unwrap(PeerTable.class);
            assertThat(pt.getPeerTable()).anyMatch(p -> p.getState(PeerState.class) == PeerState.OKAY);
        });

        Session ccSession = peerSessionFactory.getNewSession();

        // First CCR-Initial — should debit and persist
        IMessage firstCca = sendAndAwait(ccSession, gy, 0);

        long balanceAfterFirst = accounts.findById(DEMO_MSISDN).orElseThrow().getBalanceUnits();
        long ledgerRowsAfterFirst = ledger.findBySessionIdOrderByIdAsc(ccSession.getSessionId()).size();

        // Replay — same session-id, same request-number=0
        IMessage replayCca = sendAndAwait(ccSession, gy, 0);

        long balanceAfterReplay = accounts.findById(DEMO_MSISDN).orElseThrow().getBalanceUnits();
        long ledgerRowsAfterReplay = ledger.findBySessionIdOrderByIdAsc(ccSession.getSessionId()).size();

        // 1. Both CCAs returned the same Result-Code + Granted-Service-Unit
        assertThat(firstCca.getResultCode()).isEqualTo(2001L);
        assertThat(replayCca.getResultCode()).isEqualTo(firstCca.getResultCode());
        assertThat(grantedSeconds(replayCca)).isEqualTo(grantedSeconds(firstCca));

        // 2. Balance debited exactly once (1800 -> 1740)
        assertThat(balanceAfterFirst).isEqualTo(1740L);
        assertThat(balanceAfterReplay)
            .as("replay must NOT re-debit the balance")
            .isEqualTo(balanceAfterFirst);

        // 3. Ledger has only one RESERVE row, not two
        assertThat(ledgerRowsAfterFirst).isEqualTo(1L);
        assertThat(ledgerRowsAfterReplay)
            .as("replay must NOT emit a duplicate ledger row")
            .isEqualTo(ledgerRowsAfterFirst);
    }

    private IMessage sendAndAwait(Session session, ApplicationId gy, int requestNumber) throws Exception {
        Request req = session.createRequest(272, gy, "pseonkyaw.dev");
        AvpSet avps = req.getAvps();
        avps.addAvp(AvpCodes.AUTH_APPLICATION_ID, APP_ID_GY, true, false, true);
        avps.addAvp(AvpCodes.SERVICE_CONTEXT_ID,  "32251@3gpp.org", false);
        avps.addAvp(AvpCodes.CC_REQUEST_TYPE,     1, true, false);
        avps.addAvp(AvpCodes.CC_REQUEST_NUMBER,   requestNumber, true, false);
        AvpSet sub = avps.addGroupedAvp(AvpCodes.SUBSCRIPTION_ID, true, false);
        sub.addAvp(AvpCodes.SUBSCRIPTION_ID_TYPE, 0, true, false);
        sub.addAvp(AvpCodes.SUBSCRIPTION_ID_DATA, DEMO_MSISDN, false);
        AvpSet rsu = avps.addGroupedAvp(AvpCodes.REQUESTED_SERVICE_UNIT, true, false);
        rsu.addAvp(AvpCodes.CC_TIME, 60L, true, false, true);

        AnswerCapture cap = new AnswerCapture();
        session.send(req, cap);
        await().atMost(10, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> cap.complete);
        return cap.answer;
    }

    private static long grantedSeconds(IMessage cca) throws Exception {
        Avp grouped = cca.getAvps().getAvp(AvpCodes.GRANTED_SERVICE_UNIT);
        if (grouped == null) return 0L;
        return grouped.getGrouped().getAvp(AvpCodes.CC_TIME).getUnsigned32();
    }

    private static Stack bootPeerStack() throws Exception {
        try (InputStream xml = IdempotencyReplayIntegrationTest.class
                .getResourceAsStream("/diameter-client-test.xml")) {
            if (xml == null) {
                throw new IllegalStateException("/diameter-client-test.xml not on classpath");
            }
            Stack stack = new StackImpl();
            stack.init(new XMLConfiguration(xml));
            return stack;
        }
    }

    private static class AnswerCapture implements EventListener<Request, org.jdiameter.api.Answer> {
        volatile IMessage answer;
        volatile boolean complete;

        @Override
        public void receivedSuccessMessage(Request request, org.jdiameter.api.Answer ans) {
            this.answer = (IMessage) ans;
            this.complete = true;
        }

        @Override
        public void timeoutExpired(Request request) {
            this.complete = true;
        }
    }
}
