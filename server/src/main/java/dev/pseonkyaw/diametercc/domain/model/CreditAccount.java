package dev.pseonkyaw.diametercc.domain.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Subscriber prepaid credit account.
 *
 * <p>The {@code balance_units} column is mutated transactionally by
 * {@code LedgerService} on every reserve / debit / refund. Every change
 * also emits one immutable {@link LedgerTransaction} row for audit.
 *
 * <p>Currency is implicit — {@code unit_type} captures the unit
 * (e.g. {@code CC_TIME} = seconds). Multi-currency is out of scope.
 */
@Entity
@Table(name = "credit_account")
public class CreditAccount {

    @Id
    @Column(length = 20)
    private String msisdn;

    @Column(name = "balance_units", nullable = false)
    private long balanceUnits;

    @Column(name = "unit_type", nullable = false, length = 32)
    private String unitType;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CreditAccount() {}

    public CreditAccount(String msisdn, long balanceUnits, String unitType) {
        this.msisdn = msisdn;
        this.balanceUnits = balanceUnits;
        this.unitType = unitType;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getMsisdn()        { return msisdn; }
    public long getBalanceUnits()    { return balanceUnits; }
    public String getUnitType()      { return unitType; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion()         { return version; }

    public void setBalanceUnits(long balanceUnits) {
        this.balanceUnits = balanceUnits;
        this.updatedAt = OffsetDateTime.now();
    }
}
