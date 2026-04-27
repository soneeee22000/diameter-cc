package dev.pseonkyaw.diametercc.gy;

/**
 * Parsed Diameter Credit-Control-Answer (CCA), Command-Code 272 with R-bit clear.
 *
 * <p>RFC 4006 §3.2. Mandatory AVPs are non-null. Optional AVPs nullable.
 *
 * @param sessionId         Session-Id (263) — echoed from CCR
 * @param resultCode        Result-Code (268) — see {@link ResultCode}
 * @param originHost        Origin-Host (264) — local server identity
 * @param originRealm       Origin-Realm (296) — local server realm
 * @param authApplicationId Auth-Application-Id (258) — 4 for Gy
 * @param ccRequestType     CC-Request-Type (416) — echoed
 * @param ccRequestNumber   CC-Request-Number (415) — echoed
 * @param grantedUnits      Granted-Service-Unit (431) — quota allocated; nullable on TERMINATION
 * @param validityTime      Validity-Time (448) — seconds the grant is valid; nullable
 */
public record CreditControlAnswer(
        String sessionId,
        int resultCode,
        String originHost,
        String originRealm,
        long authApplicationId,
        CcRequestType ccRequestType,
        int ccRequestNumber,
        ServiceUnit grantedUnits,
        Integer validityTime
) {
}
