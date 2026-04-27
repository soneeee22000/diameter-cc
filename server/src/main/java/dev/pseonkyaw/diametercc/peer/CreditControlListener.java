package dev.pseonkyaw.diametercc.peer;

import org.jdiameter.api.Answer;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Inbound entry point for Diameter Credit-Control requests (Application-Id 4).
 *
 * <p>Block 2 scope: log the request and return {@code null} so jdiameter does
 * not auto-respond. Subsequent blocks will:
 * <ul>
 *   <li>Block 3 — parse the CCR via {@code AvpCodec} into a typed
 *       {@code CreditControlRequest} record.</li>
 *   <li>Block 5–6 — orchestrate the credit-control flow via
 *       {@code CreditControlService} and return a built CCA.</li>
 * </ul>
 *
 * <p>The Diameter Command-Code for CCR/CCA is {@code 272} (RFC 4006 §3).
 */
@Component
public class CreditControlListener implements NetworkReqListener {

    private static final Logger log = LoggerFactory.getLogger(CreditControlListener.class);

    /** Diameter Command-Code for Credit-Control-Request / Credit-Control-Answer. */
    public static final int COMMAND_CODE_CCR_CCA = 272;

    @Override
    public Answer processRequest(Request request) {
        if (request.getCommandCode() == COMMAND_CODE_CCR_CCA) {
            log.info(
                "Received CCR — session-id={} application-id={} hop-by-hop={} end-to-end={}",
                request.getSessionId(),
                request.getApplicationId(),
                request.getHopByHopIdentifier(),
                request.getEndToEndIdentifier()
            );
        } else {
            log.warn("Received unexpected Diameter request — command-code={}", request.getCommandCode());
        }

        // Block 2 stub: returning null lets jdiameter handle the timeout.
        // Block 6 will build and return a real CCA via AvpCodec + CreditControlService.
        return null;
    }
}
