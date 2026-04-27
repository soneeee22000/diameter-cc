package dev.pseonkyaw.diametercc.gy;

/**
 * Thrown when a Diameter request cannot be parsed — typically because a
 * mandatory AVP is missing, an enum value is out of range, or the wire
 * representation does not match the expected type.
 *
 * <p>The corresponding response Result-Code is RFC 6733 §7.1:
 * 5005 (DIAMETER_MISSING_AVP) or 5004 (DIAMETER_INVALID_AVP_VALUE).
 */
public class AvpParseException extends RuntimeException {

    public AvpParseException(String message) {
        super(message);
    }

    public AvpParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
