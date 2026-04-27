package dev.pseonkyaw.diametercctest;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.DisconnectCause;
import org.jdiameter.api.Network;
import org.jdiameter.api.PeerState;
import org.jdiameter.api.PeerTable;
import org.jdiameter.api.Stack;
import org.jdiameter.server.impl.StackImpl;
import org.jdiameter.server.impl.helpers.XMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bundled PDN Gateway simulator — drives Diameter Gy traffic against the
 * diameter-cc server.
 *
 * Day 1 spike scope: connect, complete CER/CEA, log peer state, exit.
 * Day 2 will add the CCR-I → CCR-U → CCR-T scenario runner.
 */
public final class DiameterTestClient {

    private static final Logger log = LoggerFactory.getLogger(DiameterTestClient.class);

    /** Standard Diameter Credit-Control Application-Id, RFC 4006 §1.1. */
    private static final long APP_ID_GY = 4L;

    public static void main(String[] args) throws Exception {
        log.info("=== diameter-cc test client starting ===");

        // Use the server StackImpl on the client side too: it supports both initiating
        // and accepting connections, and exposes the Network interface needed to register
        // application IDs (the pure-client StackImpl does not).
        Stack stack = new StackImpl();
        try (InputStream xml = DiameterTestClient.class.getResourceAsStream("/diameter-client.xml")) {
            if (xml == null) {
                throw new IllegalStateException("/diameter-client.xml not on classpath");
            }
            stack.init(new XMLConfiguration(xml));
        }

        // Register App-Id 4 so the CER advertises it; otherwise the server returns
        // CEA Result-Code 5010 (NO_COMMON_APPLICATION).
        Network network = stack.unwrap(Network.class);
        ApplicationId gy = ApplicationId.createByAuthAppId(APP_ID_GY);
        network.addNetworkReqListener(req -> {
            log.info("Test client received unexpected Diameter request: command-code={}", req.getCommandCode());
            return null;
        }, gy);

        stack.start();
        log.info("Test client Stack started — waiting up to 15s for peer to reach OKAY state");

        boolean opened = waitForPeerOpen(stack, 15_000);
        if (opened) {
            log.info("PEER OPEN — CER/CEA handshake complete. Day 1 kill-criterion PASSED.");
        } else {
            log.error("PEER NOT OPEN after 15s — CER/CEA handshake FAILED. Investigate.");
        }

        log.info("Stopping Stack...");
        stack.stop(0L, TimeUnit.MILLISECONDS, DisconnectCause.REBOOTING);
        stack.destroy();
        log.info("=== diameter-cc test client done ===");

        System.exit(opened ? 0 : 1);
    }

    private static boolean waitForPeerOpen(Stack stack, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        PeerTable peerTable = stack.unwrap(PeerTable.class);
        while (System.currentTimeMillis() < deadline) {
            boolean anyOpen = peerTable.getPeerTable().stream()
                    .anyMatch(p -> p.getState(PeerState.class) == PeerState.OKAY);
            if (anyOpen) {
                peerTable.getPeerTable().forEach(p ->
                    log.info("Peer {} state={}", p.getUri(), p.getState(PeerState.class)));
                return true;
            }
            Thread.sleep(200);
        }
        peerTable.getPeerTable().forEach(p ->
            log.warn("Peer {} state={} (after timeout)", p.getUri(), p.getState(PeerState.class)));
        return false;
    }
}
