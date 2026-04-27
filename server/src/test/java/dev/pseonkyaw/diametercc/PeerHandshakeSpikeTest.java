package dev.pseonkyaw.diametercc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.DisconnectCause;
import org.jdiameter.api.Network;
import org.jdiameter.api.PeerState;
import org.jdiameter.api.PeerTable;
import org.jdiameter.api.Stack;
import org.jdiameter.server.impl.StackImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Day 1 hour-8 KILL CRITERION test.
 *
 * Verifies that two jdiameter Stacks (one server, one client) running in
 * the same JVM can complete the CER/CEA peer handshake.
 *
 * If this test fails, the jdiameter library is not behaving — pivot to
 * test-client-only scope per ARCHITECTURE-diameter.md §10.
 */
class PeerHandshakeSpikeTest {

    private static final Logger log = LoggerFactory.getLogger(PeerHandshakeSpikeTest.class);

    private static final long VENDOR_3GPP = 10415L;
    private static final long APP_ID_GY = 4L;

    private Stack serverStack;
    private Stack clientStack;

    @AfterEach
    void tearDown() throws Exception {
        if (clientStack != null) {
            clientStack.stop(0L, TimeUnit.MILLISECONDS, DisconnectCause.REBOOTING);
            clientStack.destroy();
        }
        if (serverStack != null) {
            serverStack.stop(0L, TimeUnit.MILLISECONDS, DisconnectCause.REBOOTING);
            serverStack.destroy();
        }
    }

    @Test
    @DisplayName("KILL-CRITERION: two jdiameter Stacks complete CER/CEA within 15 seconds")
    void twoStacksCompleteCerCea() throws Exception {
        // 1. Boot server Stack on 127.0.0.1:3868
        serverStack = bootServerStack("/diameter-server-test.xml");
        Network serverNetwork = serverStack.unwrap(Network.class);
        // Standard Diameter Credit-Control App (RFC 4006) is non-vendor-specific:
        // Vendor-Id = 0, Auth-Application-Id = 4.
        ApplicationId gy = ApplicationId.createByAuthAppId(APP_ID_GY);
        serverNetwork.addNetworkReqListener(req -> {
            log.info("Server received Diameter request: command-code={}", req.getCommandCode());
            return null;
        }, gy);
        serverStack.start();
        log.info("Server Stack started on 127.0.0.1:3868");

        // 2. Boot client Stack on 127.0.0.2:3869 — initiates connection to server
        clientStack = bootClientStack("/diameter-client-test.xml");
        Network clientNetwork = clientStack.unwrap(Network.class);
        // Client must also register App-Id 4 so its CER advertises support;
        // otherwise the server returns CEA Result-Code 5010 (NO_COMMON_APPLICATION).
        clientNetwork.addNetworkReqListener(req -> {
            log.info("Client received Diameter request (unexpected): command-code={}", req.getCommandCode());
            return null;
        }, gy);
        clientStack.start();
        log.info("Client Stack started — attempting to connect to server");

        // 3. Wait for at least one peer on the client side to reach OKAY (RFC 6733 R-Open / I-Open)
        await().atMost(15, TimeUnit.SECONDS)
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                PeerTable pt = clientStack.unwrap(PeerTable.class);
                assertThat(pt.getPeerTable())
                    .as("Client peer table — at least one peer should be OKAY (CER/CEA done)")
                    .anyMatch(p -> p.getState(PeerState.class) == PeerState.OKAY);
            });

        log.info("✓ KILL CRITERION PASSED — CER/CEA handshake complete");
        clientStack.unwrap(PeerTable.class).getPeerTable().forEach(p ->
            log.info("  client peer: {} state={}", p.getUri(), p.getState(PeerState.class)));
    }

    private static Stack bootServerStack(String xmlClasspath) throws Exception {
        try (InputStream xml = PeerHandshakeSpikeTest.class.getResourceAsStream(xmlClasspath)) {
            if (xml == null) {
                throw new IllegalStateException("Missing classpath resource: " + xmlClasspath);
            }
            Stack stack = new StackImpl();
            stack.init(new org.jdiameter.server.impl.helpers.XMLConfiguration(xml));
            return stack;
        }
    }

    private static Stack bootClientStack(String xmlClasspath) throws Exception {
        // Use the server StackImpl for the test client too — it supports both initiating
        // and accepting connections, and exposes the Network interface needed to register
        // application IDs (the pure-client StackImpl does not expose Network).
        try (InputStream xml = PeerHandshakeSpikeTest.class.getResourceAsStream(xmlClasspath)) {
            if (xml == null) {
                throw new IllegalStateException("Missing classpath resource: " + xmlClasspath);
            }
            Stack stack = new StackImpl();
            stack.init(new org.jdiameter.server.impl.helpers.XMLConfiguration(xml));
            return stack;
        }
    }
}
