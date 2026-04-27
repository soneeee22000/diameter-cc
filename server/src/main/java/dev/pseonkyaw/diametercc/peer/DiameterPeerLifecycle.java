package dev.pseonkyaw.diametercc.peer;

import jakarta.annotation.PreDestroy;

import java.util.concurrent.TimeUnit;

import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.DisconnectCause;
import org.jdiameter.api.Network;
import org.jdiameter.api.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Owns the jdiameter Stack lifecycle.
 *
 * Starts the Stack once the Spring context is fully ready (so any
 * {@code NetworkReqListener} beans are wired before the network accepts
 * connections), and stops it cleanly on shutdown.
 */
@Component
public class DiameterPeerLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DiameterPeerLifecycle.class);

    /**
     * Standard Diameter Credit-Control Application-Id, RFC 4006 §1.1.
     * Non-vendor-specific (Vendor-Id = 0).
     */
    public static final long APP_ID_GY = 4L;

    private final Stack stack;
    private final CreditControlListener creditControlListener;

    public DiameterPeerLifecycle(Stack stack, CreditControlListener creditControlListener) {
        this.stack = stack;
        this.creditControlListener = creditControlListener;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws Exception {
        Network network = stack.unwrap(Network.class);
        ApplicationId gy = ApplicationId.createByAuthAppId(APP_ID_GY);
        network.addNetworkReqListener(creditControlListener, gy);

        stack.start();
        log.info("Diameter Stack started — Gy (App-Id {}) listener registered, accepting peer connections", APP_ID_GY);
    }

    @PreDestroy
    public void stop() {
        try {
            log.info("Stopping Diameter Stack...");
            stack.stop(0L, TimeUnit.MILLISECONDS, DisconnectCause.REBOOTING);
            log.info("Diameter Stack stopped");
        } catch (Exception e) {
            log.error("Error stopping Diameter Stack", e);
        }
    }
}
