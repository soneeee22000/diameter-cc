package dev.pseonkyaw.diametercc.gy;

/**
 * Subscription-Id grouped AVP (RFC 4006 §8.46, AVP code 443).
 *
 * @param type subscription-id type, e.g. END_USER_E164=0 (MSISDN), END_USER_IMSI=1, END_USER_SIP_URI=2, END_USER_NAI=3, END_USER_PRIVATE=4
 * @param data the identifier value (UTF8String); for END_USER_E164 this is the MSISDN
 */
public record SubscriptionId(int type, String data) {

    public static final int END_USER_E164    = 0;
    public static final int END_USER_IMSI    = 1;
    public static final int END_USER_SIP_URI = 2;
    public static final int END_USER_NAI     = 3;
    public static final int END_USER_PRIVATE = 4;
}
