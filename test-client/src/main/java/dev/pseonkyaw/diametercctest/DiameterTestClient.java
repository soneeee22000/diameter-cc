package dev.pseonkyaw.diametercctest;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bundled PDN Gateway simulator. By default runs one full prepaid call
 * cycle (CCR-I → CCR-U → CCR-T) against a 60-second prepaid grant; with
 * the {@code --loop} flag, repeats indefinitely so the Grafana dashboard
 * has continuous data to display.
 */
public final class DiameterTestClient {

    private static final Logger log = LoggerFactory.getLogger(DiameterTestClient.class);

    /** Standard Diameter Credit-Control Application-Id, RFC 4006 §1.1. */
    private static final long APP_ID_GY = 4L;
    private static final String DEMO_MSISDN = System.getenv().getOrDefault("DEMO_MSISDN", "33745146129");

    public static void main(String[] args) throws Exception {
        boolean loop = false;
        long sleepMs = 3000L;
        for (String a : args) {
            if (a.equals("--loop")) loop = true;
            else if (a.startsWith("--sleep=")) sleepMs = Long.parseLong(a.substring("--sleep=".length()));
        }

        log.info("=== diameter-cc test client starting (loop={}, sleep={}ms) ===", loop, sleepMs);

        Stack stack = new StackImpl();
        try (InputStream xml = DiameterTestClient.class.getResourceAsStream("/diameter-client.xml")) {
            if (xml == null) {
                throw new IllegalStateException("/diameter-client.xml not on classpath");
            }
            stack.init(new XMLConfiguration(xml));
        }

        Network network = stack.unwrap(Network.class);
        ApplicationId gy = ApplicationId.createByAuthAppId(APP_ID_GY);
        network.addNetworkReqListener(req -> null, gy);

        stack.start();
        log.info("Test client Stack started — waiting up to 15s for peer to reach OKAY");
        if (!waitForPeerOpen(stack, 15_000)) {
            log.error("PEER NOT OPEN — exiting");
            stack.stop(0L, TimeUnit.MILLISECONDS, DisconnectCause.REBOOTING);
            stack.destroy();
            System.exit(1);
        }
        log.info("PEER OPEN — starting demo scenario");

        SessionFactory sessionFactory = stack.getSessionFactory();
        long iteration = 0;
        do {
            iteration++;
            try {
                runCallCycle(sessionFactory, gy, iteration);
            } catch (Exception e) {
                log.error("Iteration {} failed", iteration, e);
            }
            if (loop) {
                Thread.sleep(sleepMs);
            }
        } while (loop);

        log.info("Stopping Stack...");
        stack.stop(0L, TimeUnit.MILLISECONDS, DisconnectCause.REBOOTING);
        stack.destroy();
        log.info("=== diameter-cc test client done ===");
    }

    /**
     * Drive one full prepaid call cycle:
     * CCR-Initial (request 60s) → CCR-Update (used 30s, request 30s more)
     * → CCR-Termination (final used 25s).
     */
    private static void runCallCycle(SessionFactory sf, ApplicationId gy, long iter) throws Exception {
        Session ccs = sf.getNewSession();
        log.info("[#{}] starting call cycle session-id={}", iter, ccs.getSessionId());

        IMessage cca1 = sendAndAwait(ccs, gy, buildCcr(ccs, gy, 1, 0, 60L, 0L, null), "CCR-I");
        log.info("[#{}] CCA-I result-code={} granted={}s", iter, rc(cca1), cct(cca1, 431));

        Thread.sleep(500);

        IMessage cca2 = sendAndAwait(ccs, gy, buildCcr(ccs, gy, 2, 1, 30L, 30L, null), "CCR-U");
        log.info("[#{}] CCA-U result-code={} new-granted={}s", iter, rc(cca2), cct(cca2, 431));

        Thread.sleep(500);

        IMessage cca3 = sendAndAwait(ccs, gy, buildCcr(ccs, gy, 3, 2, 0L, 25L, 1), "CCR-T");
        log.info("[#{}] CCA-T result-code={}", iter, rc(cca3));
    }

    private static Request buildCcr(Session session, ApplicationId gy,
                                    int ccRequestType, int ccRequestNumber,
                                    long requestedSeconds, long usedSeconds, Integer terminationCause) {
        Request req = session.createRequest(272, gy, "pseonkyaw.dev");
        AvpSet avps = req.getAvps();
        avps.addAvp(258 /* Auth-Application-Id */, APP_ID_GY, true, false, true);
        avps.addAvp(461 /* Service-Context-Id */, "32251@3gpp.org", false);
        avps.addAvp(416 /* CC-Request-Type */, ccRequestType, true, false);
        avps.addAvp(415 /* CC-Request-Number */, ccRequestNumber, true, false);

        AvpSet sub = avps.addGroupedAvp(443 /* Subscription-Id */, true, false);
        sub.addAvp(450 /* Subscription-Id-Type */, 0, true, false);
        sub.addAvp(444 /* Subscription-Id-Data */, DEMO_MSISDN, false);

        if (requestedSeconds > 0) {
            AvpSet rsu = avps.addGroupedAvp(437 /* Requested-Service-Unit */, true, false);
            rsu.addAvp(420 /* CC-Time */, requestedSeconds, true, false, true);
        }
        if (usedSeconds > 0) {
            AvpSet usu = avps.addGroupedAvp(446 /* Used-Service-Unit */, true, false);
            usu.addAvp(420 /* CC-Time */, usedSeconds, true, false, true);
        }
        if (terminationCause != null) {
            avps.addAvp(295 /* Termination-Cause */, terminationCause, true, false);
        }
        return req;
    }

    private static IMessage sendAndAwait(Session session, ApplicationId gy, Request req, String tag) throws Exception {
        AnswerCapture cap = new AnswerCapture();
        session.send(req, cap);
        if (!cap.latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(tag + " timed out");
        }
        IMessage answer = cap.answer.get();
        if (answer == null) {
            throw new IllegalStateException(tag + " produced no answer");
        }
        return answer;
    }

    private static int rc(IMessage m) {
        try {
            return m.getResultCode().getInteger32();
        } catch (Exception e) { return -1; }
    }

    private static long cct(IMessage m, int outerCode) {
        try {
            Avp grouped = m.getAvps().getAvp(outerCode);
            if (grouped == null) return -1;
            Avp ccTime = grouped.getGrouped().getAvp(420 /* CC-Time */);
            return ccTime == null ? -1 : ccTime.getUnsigned32();
        } catch (Exception e) { return -1; }
    }

    private static boolean waitForPeerOpen(Stack stack, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        PeerTable peerTable = stack.unwrap(PeerTable.class);
        while (System.currentTimeMillis() < deadline) {
            boolean anyOpen = peerTable.getPeerTable().stream()
                .anyMatch(p -> p.getState(PeerState.class) == PeerState.OKAY);
            if (anyOpen) return true;
            Thread.sleep(200);
        }
        return false;
    }

    private static class AnswerCapture implements EventListener<Request, org.jdiameter.api.Answer> {
        final AtomicReference<IMessage> answer = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void receivedSuccessMessage(Request request, org.jdiameter.api.Answer ans) {
            answer.set((IMessage) ans);
            latch.countDown();
        }

        @Override
        public void timeoutExpired(Request request) {
            latch.countDown();
        }
    }
}
