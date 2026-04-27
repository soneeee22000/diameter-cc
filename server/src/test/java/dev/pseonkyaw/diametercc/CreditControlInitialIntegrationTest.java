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

import dev.pseonkyaw.diametercc.domain.model.CcSession;
import dev.pseonkyaw.diametercc.domain.model.LedgerTransaction;
import dev.pseonkyaw.diametercc.domain.repository.CcSessionRepository;
import dev.pseonkyaw.diametercc.domain.repository.CreditAccountRepository;
import dev.pseonkyaw.diametercc.domain.repository.LedgerTransactionRepository;
import dev.pseonkyaw.diametercc.gy.AvpCodes;

/**
 * End-to-end Day 1 deliverable check: a peer client sends a real CCR-Initial
 * over the wire to the production Spring Boot server, and we assert on:
 *
 * <ol>
 *   <li>CCA returned with Result-Code 2001 (DIAMETER_SUCCESS)</li>
 *   <li>Granted-Service-Unit / CC-Time matches the requested 60s</li>
 *   <li>{@code credit_account.balance_units} decremented by 60</li>
 *   <li>One Op.RESERVE row in {@code ledger_transaction}</li>
 *   <li>A row in {@code cc_session} with state OPEN</li>
 * </ol>
 *
 * <p>Gated on env ENABLE_DOCKER_TESTS=true.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "diameter.listen-port=3868"
})
@EnabledIfEnvironmentVariable(named = "ENABLE_DOCKER_TESTS", matches = "true",
    disabledReason = "Set ENABLE_DOCKER_TESTS=true to run Docker-based end-to-end tests")
class CreditControlInitialIntegrationTest {

    private static final long APP_ID_GY = 4L;
    private static final String DEMO_MSISDN = "33745146129";

    @Autowired CreditAccountRepository accounts;
    @Autowired CcSessionRepository sessions;
    @Autowired LedgerTransactionRepository ledger;

    private Stack peerStack;
    private SessionFactory peerSessionFactory;

    @BeforeEach
    void resetBalance() {
        accounts.findById(DEMO_MSISDN).ifPresent(a -> {
            if (a.getBalanceUnits() != 600L) {
                a.setBalanceUnits(600L);
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
    @DisplayName("CCR-Initial → CCA-I (2001) with 60s grant; balance -60; one RESERVE row; OPEN session")
    void ccrInitialDebitsBalanceAndGrantsQuota() throws Exception {
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

        AnswerCapture answerCapture = new AnswerCapture();
        Session ccSession = peerSessionFactory.getNewSession();
        Request ccr = buildCcrInitial(ccSession, gy, DEMO_MSISDN, 60L);
        ccSession.send(ccr, answerCapture);

        await().atMost(10, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS)
            .until(answerCapture::isComplete);

        IMessage cca = answerCapture.answer;
        assertThat(cca).as("CCA received").isNotNull();
        assertThat(cca.getResultCode()).isEqualTo(2001L);

        Avp grantedGroup = cca.getAvps().getAvp(AvpCodes.GRANTED_SERVICE_UNIT);
        assertThat(grantedGroup).as("Granted-Service-Unit AVP").isNotNull();
        AvpSet grantedInner = grantedGroup.getGrouped();
        assertThat(grantedInner.getAvp(AvpCodes.CC_TIME).getUnsigned32()).isEqualTo(60L);

        assertThat(accounts.findById(DEMO_MSISDN).orElseThrow().getBalanceUnits())
            .as("balance debited by granted units").isEqualTo(540L);

        var ledgerRows = ledger.findBySessionIdOrderByIdAsc(ccSession.getSessionId());
        assertThat(ledgerRows).hasSize(1);
        assertThat(ledgerRows.get(0).getOp()).isEqualTo(LedgerTransaction.Op.RESERVE);
        assertThat(ledgerRows.get(0).getUnits()).isEqualTo(60L);
        assertThat(ledgerRows.get(0).getBalanceAfter()).isEqualTo(540L);

        CcSession openSession = sessions.findById(ccSession.getSessionId()).orElseThrow();
        assertThat(openSession.getState()).isEqualTo(CcSession.State.OPEN);
        assertThat(openSession.getMsisdn()).isEqualTo(DEMO_MSISDN);
        assertThat(openSession.getGrantedUnitsTotal()).isEqualTo(60L);
    }

    private static Request buildCcrInitial(Session session, ApplicationId gy, String msisdn, long requestedSeconds) {
        Request req = session.createRequest(272, gy, "pseonkyaw.dev");
        AvpSet avps = req.getAvps();
        avps.addAvp(AvpCodes.AUTH_APPLICATION_ID,  APP_ID_GY, true, false, true);
        avps.addAvp(AvpCodes.SERVICE_CONTEXT_ID,   "32251@3gpp.org", false);
        avps.addAvp(AvpCodes.CC_REQUEST_TYPE,      1, true, false);
        avps.addAvp(AvpCodes.CC_REQUEST_NUMBER,    0, true, false);

        AvpSet sub = avps.addGroupedAvp(AvpCodes.SUBSCRIPTION_ID, true, false);
        sub.addAvp(AvpCodes.SUBSCRIPTION_ID_TYPE, 0, true, false);
        sub.addAvp(AvpCodes.SUBSCRIPTION_ID_DATA, msisdn, false);

        AvpSet rsu = avps.addGroupedAvp(AvpCodes.REQUESTED_SERVICE_UNIT, true, false);
        rsu.addAvp(AvpCodes.CC_TIME, requestedSeconds, true, false, true);

        return req;
    }

    private static Stack bootPeerStack() throws Exception {
        try (InputStream xml = CreditControlInitialIntegrationTest.class
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

        boolean isComplete() {
            return complete;
        }
    }
}
