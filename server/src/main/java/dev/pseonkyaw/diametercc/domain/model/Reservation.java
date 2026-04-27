package dev.pseonkyaw.diametercc.domain.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-CCR state row keyed on the idempotency pair (Session-Id, CC-Request-
 * Number). Stores both the business decision (units granted / debited /
 * Result-Code) and the exact CCA AVP blob that was returned, enabling
 * byte-identical replay if the same CCR arrives twice.
 *
 * <p>Rationale captured in {@code docs/ARCHITECTURE-diameter.md} ADR-003 / 004.
 */
@Entity
@Table(name = "reservation")
@IdClass(ReservationKey.class)
public class Reservation {

    @Id
    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Id
    @Column(name = "cc_request_number")
    private int ccRequestNumber;

    @Column(name = "cc_request_type", nullable = false)
    private short ccRequestType;

    @Column(name = "requested_units")
    private Long requestedUnits;

    @Column(name = "used_units", nullable = false)
    private long usedUnits;

    @Column(name = "granted_units", nullable = false)
    private long grantedUnits;

    @Column(name = "result_code", nullable = false)
    private int resultCode;

    @Column(name = "cca_avp_blob", nullable = false, columnDefinition = "BYTEA")
    private byte[] ccaAvpBlob;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Reservation() {}

    public Reservation(String sessionId,
                       int ccRequestNumber,
                       short ccRequestType,
                       Long requestedUnits,
                       long usedUnits,
                       long grantedUnits,
                       int resultCode,
                       byte[] ccaAvpBlob) {
        this.sessionId = sessionId;
        this.ccRequestNumber = ccRequestNumber;
        this.ccRequestType = ccRequestType;
        this.requestedUnits = requestedUnits;
        this.usedUnits = usedUnits;
        this.grantedUnits = grantedUnits;
        this.resultCode = resultCode;
        this.ccaAvpBlob = ccaAvpBlob;
    }

    public String getSessionId()        { return sessionId; }
    public int getCcRequestNumber()     { return ccRequestNumber; }
    public short getCcRequestType()     { return ccRequestType; }
    public Long getRequestedUnits()     { return requestedUnits; }
    public long getUsedUnits()          { return usedUnits; }
    public long getGrantedUnits()       { return grantedUnits; }
    public int getResultCode()          { return resultCode; }
    public byte[] getCcaAvpBlob()       { return ccaAvpBlob; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
