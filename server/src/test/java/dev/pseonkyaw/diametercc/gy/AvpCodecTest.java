package dev.pseonkyaw.diametercc.gy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jdiameter.api.AvpSet;
import org.jdiameter.api.Request;
import org.jdiameter.client.api.IMessage;
import org.jdiameter.client.impl.parser.MessageParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AvpCodecTest {

    private static final MessageParser PARSER = new MessageParser();

    private static Request emptyCcr() {
        IMessage msg = PARSER.createEmptyMessage(272, 4L);
        return msg;
    }

    private static AvpSet baseCcrInitialAvps(Request r) {
        AvpSet a = r.getAvps();
        a.addAvp(AvpCodes.SESSION_ID,           "test-client.local;1;1;abcd1234", false);
        a.addAvp(AvpCodes.ORIGIN_HOST,          "test-client.local".getBytes(), true, false);
        a.addAvp(AvpCodes.ORIGIN_REALM,         "pseonkyaw.dev".getBytes(), true, false);
        a.addAvp(AvpCodes.DESTINATION_REALM,    "pseonkyaw.dev".getBytes(), true, false);
        a.addAvp(AvpCodes.AUTH_APPLICATION_ID,  4L, true, false, true);
        a.addAvp(AvpCodes.SERVICE_CONTEXT_ID,   "32251@3gpp.org", false);
        a.addAvp(AvpCodes.CC_REQUEST_TYPE,      1, true, false);
        a.addAvp(AvpCodes.CC_REQUEST_NUMBER,    0, true, false);
        return a;
    }

    private static void addSubscriptionId(AvpSet outer, int type, String data) {
        AvpSet group = outer.addGroupedAvp(AvpCodes.SUBSCRIPTION_ID, true, false);
        group.addAvp(AvpCodes.SUBSCRIPTION_ID_TYPE, type, true, false);
        group.addAvp(AvpCodes.SUBSCRIPTION_ID_DATA, data, false);
    }

    private static void addRequestedTime(AvpSet outer, long seconds) {
        AvpSet group = outer.addGroupedAvp(AvpCodes.REQUESTED_SERVICE_UNIT, true, false);
        group.addAvp(AvpCodes.CC_TIME, seconds, true, false, true);
    }

    @Test
    @DisplayName("CCR-Initial with all mandatory + Subscription-Id + Requested-Service-Unit parses cleanly")
    void parsesFullCcrInitial() {
        Request req = emptyCcr();
        AvpSet avps = baseCcrInitialAvps(req);
        addSubscriptionId(avps, SubscriptionId.END_USER_E164, "33745146129");
        addRequestedTime(avps, 60L);

        CreditControlRequest ccr = AvpCodec.parseCcr(req);

        assertThat(ccr.sessionId()).isEqualTo("test-client.local;1;1;abcd1234");
        assertThat(ccr.originHost()).isEqualTo("test-client.local");
        assertThat(ccr.originRealm()).isEqualTo("pseonkyaw.dev");
        assertThat(ccr.destinationRealm()).isEqualTo("pseonkyaw.dev");
        assertThat(ccr.authApplicationId()).isEqualTo(4L);
        assertThat(ccr.serviceContextId()).isEqualTo("32251@3gpp.org");
        assertThat(ccr.ccRequestType()).isEqualTo(CcRequestType.INITIAL);
        assertThat(ccr.ccRequestNumber()).isZero();
        assertThat(ccr.subscriptionId()).isNotNull();
        assertThat(ccr.subscriptionId().type()).isEqualTo(SubscriptionId.END_USER_E164);
        assertThat(ccr.subscriptionId().data()).isEqualTo("33745146129");
        assertThat(ccr.requestedUnits()).isNotNull();
        assertThat(ccr.requestedUnits().ccTimeSeconds()).isEqualTo(60L);
        assertThat(ccr.usedUnits()).isNull();
        assertThat(ccr.terminationCause()).isNull();
    }

    @Test
    @DisplayName("CCR-Update without Subscription-Id parses with null subscription")
    void parsesCcrUpdateWithoutSubscriptionId() {
        Request req = emptyCcr();
        AvpSet avps = baseCcrInitialAvps(req);
        avps.removeAvp(AvpCodes.CC_REQUEST_TYPE);
        avps.removeAvp(AvpCodes.CC_REQUEST_NUMBER);
        avps.addAvp(AvpCodes.CC_REQUEST_TYPE,   2, true, false);
        avps.addAvp(AvpCodes.CC_REQUEST_NUMBER, 1, true, false);
        AvpSet usedGroup = avps.addGroupedAvp(AvpCodes.USED_SERVICE_UNIT, true, false);
        usedGroup.addAvp(AvpCodes.CC_TIME, 60L, true, false, true);

        CreditControlRequest ccr = AvpCodec.parseCcr(req);

        assertThat(ccr.ccRequestType()).isEqualTo(CcRequestType.UPDATE);
        assertThat(ccr.ccRequestNumber()).isEqualTo(1);
        assertThat(ccr.subscriptionId()).isNull();
        assertThat(ccr.usedUnits()).isNotNull();
        assertThat(ccr.usedUnits().ccTimeSeconds()).isEqualTo(60L);
        assertThat(ccr.requestedUnits()).isNull();
    }

    @Test
    @DisplayName("CCR-Termination carries Termination-Cause AVP")
    void parsesCcrTerminationWithCause() {
        Request req = emptyCcr();
        AvpSet avps = baseCcrInitialAvps(req);
        avps.removeAvp(AvpCodes.CC_REQUEST_TYPE);
        avps.removeAvp(AvpCodes.CC_REQUEST_NUMBER);
        avps.addAvp(AvpCodes.CC_REQUEST_TYPE,   3, true, false);
        avps.addAvp(AvpCodes.CC_REQUEST_NUMBER, 2, true, false);
        // Termination-Cause = LOGOUT (1) per RFC 6733 §8.47
        avps.addAvp(AvpCodes.TERMINATION_CAUSE, 1, true, false);

        CreditControlRequest ccr = AvpCodec.parseCcr(req);

        assertThat(ccr.ccRequestType()).isEqualTo(CcRequestType.TERMINATION);
        assertThat(ccr.terminationCause()).isEqualTo(1);
    }

    @Nested
    class BadRequests {

        @Test
        @DisplayName("Missing Session-Id raises AvpParseException")
        void missingSessionId() {
            Request req = emptyCcr();
            AvpSet avps = baseCcrInitialAvps(req);
            avps.removeAvp(AvpCodes.SESSION_ID);

            assertThatThrownBy(() -> AvpCodec.parseCcr(req))
                .isInstanceOf(AvpParseException.class)
                .hasMessageContaining("Missing required AVP code=" + AvpCodes.SESSION_ID);
        }

        @Test
        @DisplayName("Unknown CC-Request-Type code raises IllegalArgumentException via enum")
        void unknownCcRequestType() {
            Request req = emptyCcr();
            AvpSet avps = baseCcrInitialAvps(req);
            avps.removeAvp(AvpCodes.CC_REQUEST_TYPE);
            avps.addAvp(AvpCodes.CC_REQUEST_TYPE, 99, true, false);

            assertThatThrownBy(() -> AvpCodec.parseCcr(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown CC-Request-Type code: 99");
        }
    }
}
