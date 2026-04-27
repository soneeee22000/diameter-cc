package dev.pseonkyaw.diametercc.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link Reservation}: (Session-Id, CC-Request-Number).
 *
 * <p>This pair is the natural idempotency key per RFC 4006 §8.2 — Session-Id
 * is unique per CC session, CC-Request-Number is monotonic within a session.
 * A duplicate insert is detected at the DB layer via the composite PK
 * uniqueness constraint, which is exactly what enables replay-safe debits.
 */
public record ReservationKey(String sessionId, int ccRequestNumber) implements Serializable {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationKey k)) return false;
        return ccRequestNumber == k.ccRequestNumber && Objects.equals(sessionId, k.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, ccRequestNumber);
    }
}
