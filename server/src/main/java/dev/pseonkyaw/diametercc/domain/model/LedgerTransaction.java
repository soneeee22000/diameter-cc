package dev.pseonkyaw.diametercc.domain.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Immutable audit row — every reserve / debit / refund of {@code credit_account.balance_units}
 * emits one row here. Acts as a double-entry trail over the live balance:
 * {@code SUM(units * sign(op))} should reconcile to the current balance.
 */
@Entity
@Table(name = "ledger_transaction")
public class LedgerTransaction {

    public enum Op { RESERVE, DEBIT, REFUND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(nullable = false, length = 20)
    private String msisdn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Op op;

    @Column(nullable = false)
    private long units;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "cc_request_number")
    private Integer ccRequestNumber;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected LedgerTransaction() {}

    public LedgerTransaction(String sessionId, String msisdn, Op op, long units,
                             long balanceAfter, Integer ccRequestNumber) {
        this.sessionId = sessionId;
        this.msisdn = msisdn;
        this.op = op;
        this.units = units;
        this.balanceAfter = balanceAfter;
        this.ccRequestNumber = ccRequestNumber;
    }

    public Long getId()                   { return id; }
    public String getSessionId()          { return sessionId; }
    public String getMsisdn()             { return msisdn; }
    public Op getOp()                     { return op; }
    public long getUnits()                { return units; }
    public long getBalanceAfter()         { return balanceAfter; }
    public Integer getCcRequestNumber()   { return ccRequestNumber; }
    public OffsetDateTime getCreatedAt()  { return createdAt; }
}
