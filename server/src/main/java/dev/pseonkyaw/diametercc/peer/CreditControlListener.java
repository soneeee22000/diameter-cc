package dev.pseonkyaw.diametercc.peer;

import org.jdiameter.api.Answer;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.pseonkyaw.diametercc.gy.AvpCodec;
import dev.pseonkyaw.diametercc.gy.AvpParseException;
import dev.pseonkyaw.diametercc.gy.CreditControlRequest;

/**
 * Inbound entry point for Diameter Credit-Control requests (Application-Id 4).
 *
 * <p>Block 3 scope: parse the inbound CCR via {@link AvpCodec} and log a
 * structured summary. CCA construction is wired in Block 6 (after
 * {@code CreditControlService} exists).
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
        if (request.getCommandCode() != COMMAND_CODE_CCR_CCA) {
            log.warn("Ignoring non-CCR Diameter request — command-code={}", request.getCommandCode());
            return null;
        }

        try {
            CreditControlRequest ccr = AvpCodec.parseCcr(request);
            log.info(
                "CCR parsed — type={} session-id={} request-number={} msisdn={} requested-units={}",
                ccr.ccRequestType(),
                ccr.sessionId(),
                ccr.ccRequestNumber(),
                ccr.subscriptionId() == null ? null : ccr.subscriptionId().data(),
                ccr.requestedUnits()
            );
        } catch (AvpParseException e) {
            log.warn("Rejecting malformed CCR — {}", e.getMessage());
        }

        // Block 6 will build and return a real CCA via CreditControlService + AvpCodec.
        return null;
    }
}
