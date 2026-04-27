package dev.pseonkyaw.diametercc.gy;

/**
 * Diameter AVP code constants used by the Gy interface.
 *
 * <p>Codes from RFC 6733 (base) and RFC 4006 (credit-control).
 * Vendor-Id is 0 (IETF) for all of these; 3GPP-specific AVPs are not used.
 */
public final class AvpCodes {

    private AvpCodes() {}

    // === RFC 6733 base AVPs ===
    public static final int SESSION_ID            = 263;
    public static final int ORIGIN_HOST           = 264;
    public static final int ORIGIN_REALM          = 296;
    public static final int DESTINATION_REALM     = 283;
    public static final int DESTINATION_HOST      = 293;
    public static final int AUTH_APPLICATION_ID   = 258;
    public static final int RESULT_CODE           = 268;
    public static final int TERMINATION_CAUSE     = 295;

    // === RFC 4006 credit-control AVPs ===
    public static final int SERVICE_CONTEXT_ID    = 461;
    public static final int CC_REQUEST_TYPE       = 416;
    public static final int CC_REQUEST_NUMBER     = 415;

    public static final int SUBSCRIPTION_ID       = 443;  // grouped
    public static final int SUBSCRIPTION_ID_TYPE  = 450;
    public static final int SUBSCRIPTION_ID_DATA  = 444;

    public static final int REQUESTED_SERVICE_UNIT = 437;  // grouped
    public static final int USED_SERVICE_UNIT     = 446;   // grouped
    public static final int GRANTED_SERVICE_UNIT  = 431;   // grouped

    public static final int CC_TIME               = 420;
    public static final int CC_TOTAL_OCTETS       = 421;
    public static final int CC_INPUT_OCTETS       = 412;
    public static final int CC_OUTPUT_OCTETS      = 414;
    public static final int CC_SERVICE_SPECIFIC_UNITS = 417;

    public static final int VALIDITY_TIME         = 448;
    public static final int RATING_GROUP          = 432;
    public static final int SERVICE_IDENTIFIER    = 439;

    public static final int MULTIPLE_SERVICES_CREDIT_CONTROL = 456; // grouped
    public static final int MULTIPLE_SERVICES_INDICATOR      = 455;
}
