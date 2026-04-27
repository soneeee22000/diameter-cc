package dev.pseonkyaw.diametercc.gy;

/**
 * Generic service-unit value object — represents the contents of any of the
 * three RFC 4006 grouped AVPs that share the same shape:
 *
 * <ul>
 *   <li>Requested-Service-Unit (437)</li>
 *   <li>Granted-Service-Unit (431)</li>
 *   <li>Used-Service-Unit (446)</li>
 * </ul>
 *
 * <p>For diameter-cc we only support the CC-Time variant (single-quota policy
 * per ADR-006). Other variants (CC-Total-Octets, CC-Service-Specific-Units)
 * are out of scope.
 *
 * @param ccTimeSeconds units expressed in seconds, or {@code null} if absent
 */
public record ServiceUnit(Long ccTimeSeconds) {

    public static ServiceUnit time(long seconds) {
        return new ServiceUnit(seconds);
    }

    public static ServiceUnit empty() {
        return new ServiceUnit(null);
    }

    public boolean isEmpty() {
        return ccTimeSeconds == null;
    }
}
