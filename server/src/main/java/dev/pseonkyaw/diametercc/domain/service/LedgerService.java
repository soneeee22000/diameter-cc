package dev.pseonkyaw.diametercc.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.pseonkyaw.diametercc.domain.model.CreditAccount;
import dev.pseonkyaw.diametercc.domain.model.LedgerTransaction;
import dev.pseonkyaw.diametercc.domain.model.LedgerTransaction.Op;
import dev.pseonkyaw.diametercc.domain.repository.CreditAccountRepository;
import dev.pseonkyaw.diametercc.domain.repository.LedgerTransactionRepository;
import jakarta.persistence.EntityNotFoundException;

/**
 * Transactional primitives for the prepaid credit ledger. Every balance
 * mutation goes through one of these methods, and every method emits one
 * immutable {@link LedgerTransaction} audit row.
 *
 * <p>Composed by {@code CreditControlService} (Block 6) — this class does
 * not know about Diameter, sessions, or CCR semantics.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final CreditAccountRepository accounts;
    private final LedgerTransactionRepository ledger;

    public LedgerService(CreditAccountRepository accounts, LedgerTransactionRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /**
     * Reserve up to {@code requestedUnits} from the subscriber's balance.
     *
     * <p>Atomic. If the balance can cover the request, the full amount is
     * deducted and returned. If the balance is below the request, all
     * remaining units are granted (partial grant) — RFC 4006 allows the
     * server to grant less than requested. If balance is zero, returns 0.
     *
     * @return units actually granted (0..requestedUnits)
     */
    @Transactional
    public long reserve(String msisdn, long requestedUnits, String sessionId, int requestNumber) {
        if (requestedUnits <= 0) {
            return 0L;
        }
        CreditAccount account = accounts.findById(msisdn)
            .orElseThrow(() -> new EntityNotFoundException("Unknown MSISDN: " + msisdn));

        long granted = Math.min(requestedUnits, account.getBalanceUnits());
        if (granted == 0L) {
            log.info("Reserve denied — msisdn={} balance=0 requested={}", msisdn, requestedUnits);
            return 0L;
        }

        long newBalance = account.getBalanceUnits() - granted;
        account.setBalanceUnits(newBalance);
        accounts.save(account);

        ledger.save(new LedgerTransaction(sessionId, msisdn, Op.RESERVE, granted, newBalance, requestNumber));
        log.debug("RESERVE — msisdn={} granted={} balance_after={} session={} req#={}",
            msisdn, granted, newBalance, sessionId, requestNumber);
        return granted;
    }

    /**
     * Record final consumption of a previously-reserved amount.
     *
     * <p>The balance was already deducted at reservation time, so this method
     * just emits an audit DEBIT row capturing how many of the reserved units
     * were actually consumed. Use {@link #refund} for the unused remainder.
     */
    @Transactional
    public void recordDebit(String msisdn, long usedUnits, String sessionId, int requestNumber) {
        if (usedUnits <= 0) {
            return;
        }
        CreditAccount account = accounts.findById(msisdn)
            .orElseThrow(() -> new EntityNotFoundException("Unknown MSISDN: " + msisdn));
        ledger.save(new LedgerTransaction(
            sessionId, msisdn, Op.DEBIT, usedUnits, account.getBalanceUnits(), requestNumber));
        log.debug("DEBIT — msisdn={} used={} balance={} session={} req#={}",
            msisdn, usedUnits, account.getBalanceUnits(), sessionId, requestNumber);
    }

    /**
     * Refund unused reserved units back to the balance.
     */
    @Transactional
    public void refund(String msisdn, long unusedUnits, String sessionId, int requestNumber) {
        if (unusedUnits <= 0) {
            return;
        }
        CreditAccount account = accounts.findById(msisdn)
            .orElseThrow(() -> new EntityNotFoundException("Unknown MSISDN: " + msisdn));

        long newBalance = account.getBalanceUnits() + unusedUnits;
        account.setBalanceUnits(newBalance);
        accounts.save(account);

        ledger.save(new LedgerTransaction(
            sessionId, msisdn, Op.REFUND, unusedUnits, newBalance, requestNumber));
        log.debug("REFUND — msisdn={} amount={} balance_after={} session={} req#={}",
            msisdn, unusedUnits, newBalance, sessionId, requestNumber);
    }
}
