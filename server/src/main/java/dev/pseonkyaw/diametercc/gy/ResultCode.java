package dev.pseonkyaw.diametercc.gy;

/**
 * Diameter Result-Code values relevant to the Gy interface.
 *
 * <p>Subset only — the full registry is at IANA. We carry the codes we
 * actually emit; everything else is passed through as raw integers.
 *
 * <p>See:
 * <ul>
 *   <li>RFC 6733 §7.1 — base protocol Result-Codes (1xxx Informational, 2xxx Success, 3xxx Protocol Errors, 4xxx Transient Failures, 5xxx Permanent Failures)</li>
 *   <li>RFC 4006 §9 — credit-control specific Result-Codes</li>
 * </ul>
 */
public enum ResultCode {
    DIAMETER_SUCCESS(2001),

    DIAMETER_AUTHORIZATION_REJECTED(5003),
    DIAMETER_INVALID_AVP_VALUE(5004),
    DIAMETER_MISSING_AVP(5005),

    /** RFC 4006 §9.1 — End user's account could not be authorized due to expired credit. */
    DIAMETER_END_USER_SERVICE_DENIED(4010),

    /** RFC 4006 §9.1 — Credit-control server determined that the user's account cannot cover service cost. */
    DIAMETER_CREDIT_CONTROL_NOT_APPLICABLE(4011),

    /** RFC 4006 §9.1 — End user's account reached credit limit; service must be denied. */
    DIAMETER_CREDIT_LIMIT_REACHED(4012);

    private final int value;

    ResultCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
