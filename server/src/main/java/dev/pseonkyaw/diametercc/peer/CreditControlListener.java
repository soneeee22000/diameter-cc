package dev.pseonkyaw.diametercc.peer;

import org.jdiameter.api.Answer;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.pseonkyaw.diametercc.domain.service.CreditControlService;
import dev.pseonkyaw.diametercc.gy.AvpCodec;
import dev.pseonkyaw.diametercc.gy.AvpParseException;
import dev.pseonkyaw.diametercc.gy.CreditControlAnswer;
import dev.pseonkyaw.diametercc.gy.CreditControlRequest;

/**
 * Inbound entry point for Diameter Credit-Control requests (Application-Id 4).
 *
 * <p>Parses the CCR via {@link AvpCodec}, dispatches to
 * {@link CreditControlService}, and serializes the resulting
 * {@link CreditControlAnswer} back into a Diameter Answer.
 *
 * <p>The Diameter Command-Code for CCR/CCA is {@code 272} (RFC 4006 §3).
 */
@Component
public class CreditControlListener implements NetworkReqListener {

    private static final Logger log = LoggerFactory.getLogger(CreditControlListener.class);

    /** Diameter Command-Code for Credit-Control-Request / Credit-Control-Answer. */
    public static final int COMMAND_CODE_CCR_CCA = 272;

    private final CreditControlService creditControl;

    public CreditControlListener(CreditControlService creditControl) {
        this.creditControl = creditControl;
    }

    @Override
    public Answer processRequest(Request request) {
        if (request.getCommandCode() != COMMAND_CODE_CCR_CCA) {
            log.warn("Ignoring non-CCR Diameter request — command-code={}", request.getCommandCode());
            return null;
        }

        try {
            CreditControlRequest ccr = AvpCodec.parseCcr(request);
            CreditControlAnswer cca = creditControl.handle(ccr);
            return AvpCodec.buildCca(request, cca);
        } catch (AvpParseException e) {
            log.warn("Rejecting malformed CCR — {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error handling CCR", e);
            return null;
        }
    }
}
