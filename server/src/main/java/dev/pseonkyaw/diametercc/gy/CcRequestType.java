package dev.pseonkyaw.diametercc.gy;

/**
 * CC-Request-Type AVP values (RFC 4006 §8.3, AVP code 416).
 *
 * <p>Identifies the role of a Credit-Control-Request within a session:
 * <ul>
 *   <li>{@link #INITIAL} — first request of a session, requests an initial quota.</li>
 *   <li>{@link #UPDATE} — interim request, reports usage and requests new quota.</li>
 *   <li>{@link #TERMINATION} — final request, reports last usage and closes the session.</li>
 *   <li>{@link #EVENT} — one-shot event-based charging (no session, e.g. SMS).</li>
 * </ul>
 */
public enum CcRequestType {
    INITIAL(1),
    UPDATE(2),
    TERMINATION(3),
    EVENT(4);

    private final int code;

    CcRequestType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static CcRequestType fromCode(int code) {
        for (CcRequestType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown CC-Request-Type code: " + code);
    }
}
