package dev.pseonkyaw.diametercc.gy;

import org.jdiameter.api.Avp;
import org.jdiameter.api.AvpDataException;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.Request;

/**
 * Encode / decode for Diameter Credit-Control AVPs (RFC 4006).
 *
 * <p>Block 3 scope: parse-side only. Encoding (CCA building) ships in Block 6.
 *
 * <p>Stateless and thread-safe.
 */
public final class AvpCodec {

    private AvpCodec() {}

    /**
     * Parse a jdiameter {@link Request} (CCR, Command-Code 272) into a typed
     * {@link CreditControlRequest}.
     *
     * @throws AvpParseException if a mandatory AVP is missing or unparseable
     */
    public static CreditControlRequest parseCcr(Request request) {
        AvpSet avps = request.getAvps();

        try {
            String sessionId = required(avps, AvpCodes.SESSION_ID).getUTF8String();
            String originHost = required(avps, AvpCodes.ORIGIN_HOST).getDiameterIdentity();
            String originRealm = required(avps, AvpCodes.ORIGIN_REALM).getDiameterIdentity();
            String destinationRealm = required(avps, AvpCodes.DESTINATION_REALM).getDiameterIdentity();
            long authApplicationId = required(avps, AvpCodes.AUTH_APPLICATION_ID).getUnsigned32();
            String serviceContextId = required(avps, AvpCodes.SERVICE_CONTEXT_ID).getUTF8String();
            int ccRequestTypeCode = (int) required(avps, AvpCodes.CC_REQUEST_TYPE).getUnsigned32();
            int ccRequestNumber = (int) required(avps, AvpCodes.CC_REQUEST_NUMBER).getUnsigned32();

            CcRequestType ccRequestType = CcRequestType.fromCode(ccRequestTypeCode);

            SubscriptionId subscriptionId = parseSubscriptionId(avps);
            ServiceUnit requestedUnits = parseServiceUnit(avps, AvpCodes.REQUESTED_SERVICE_UNIT);
            ServiceUnit usedUnits = parseServiceUnit(avps, AvpCodes.USED_SERVICE_UNIT);
            Integer terminationCause = optionalInt(avps, AvpCodes.TERMINATION_CAUSE);

            return new CreditControlRequest(
                sessionId,
                originHost,
                originRealm,
                destinationRealm,
                authApplicationId,
                serviceContextId,
                ccRequestType,
                ccRequestNumber,
                subscriptionId,
                requestedUnits,
                usedUnits,
                terminationCause
            );
        } catch (AvpDataException e) {
            throw new AvpParseException("Failed to parse CCR AVP: " + e.getMessage(), e);
        }
    }

    private static SubscriptionId parseSubscriptionId(AvpSet avps) throws AvpDataException {
        Avp grouped = avps.getAvp(AvpCodes.SUBSCRIPTION_ID);
        if (grouped == null) {
            return null;
        }
        AvpSet inner = grouped.getGrouped();
        Avp typeAvp = inner.getAvp(AvpCodes.SUBSCRIPTION_ID_TYPE);
        Avp dataAvp = inner.getAvp(AvpCodes.SUBSCRIPTION_ID_DATA);
        if (typeAvp == null || dataAvp == null) {
            throw new AvpParseException(
                "Subscription-Id grouped AVP missing required Subscription-Id-Type / -Data");
        }
        return new SubscriptionId((int) typeAvp.getUnsigned32(), dataAvp.getUTF8String());
    }

    /**
     * Parse a Requested-/Used-/Granted-Service-Unit grouped AVP. We only
     * support CC-Time (per ADR-006) — other unit-types (octets, service-
     * specific-units) are read but ignored.
     */
    private static ServiceUnit parseServiceUnit(AvpSet avps, int outerCode) throws AvpDataException {
        Avp grouped = avps.getAvp(outerCode);
        if (grouped == null) {
            return null;
        }
        AvpSet inner = grouped.getGrouped();
        Avp ccTime = inner.getAvp(AvpCodes.CC_TIME);
        if (ccTime == null) {
            return ServiceUnit.empty();
        }
        return ServiceUnit.time(ccTime.getUnsigned32());
    }

    private static Avp required(AvpSet avps, int code) {
        Avp a = avps.getAvp(code);
        if (a == null) {
            throw new AvpParseException("Missing required AVP code=" + code);
        }
        return a;
    }

    private static Integer optionalInt(AvpSet avps, int code) throws AvpDataException {
        Avp a = avps.getAvp(code);
        return a == null ? null : a.getInteger32();
    }
}
