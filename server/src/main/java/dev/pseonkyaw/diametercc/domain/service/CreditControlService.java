package dev.pseonkyaw.diametercc.domain.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.pseonkyaw.diametercc.domain.model.CcSession;
import dev.pseonkyaw.diametercc.domain.model.Reservation;
import dev.pseonkyaw.diametercc.domain.model.ReservationKey;
import dev.pseonkyaw.diametercc.domain.repository.CcSessionRepository;
import dev.pseonkyaw.diametercc.domain.repository.ReservationRepository;
import dev.pseonkyaw.diametercc.gy.CcRequestType;
import dev.pseonkyaw.diametercc.gy.CreditControlAnswer;
import dev.pseonkyaw.diametercc.gy.CreditControlRequest;
import dev.pseonkyaw.diametercc.gy.ResultCode;
import dev.pseonkyaw.diametercc.gy.ServiceUnit;
import dev.pseonkyaw.diametercc.gy.SubscriptionId;

/**
 * Orchestrates the Diameter Credit-Control flow on top of {@link LedgerService}.
 *
 * <p>The single entry point {@link #handle(CreditControlRequest)} routes by
 * CC-Request-Type to {@link #handleInitial}, {@link #handleUpdate}, or
 * {@link #handleTerminate}. All four methods share the same idempotency
 * gate: if a CCR with the same (Session-Id, CC-Request-Number) has been
 * processed before, the cached answer is returned without re-debiting.
 *
 * <p>Block 6 scope: handleInitial only. Update / Terminate stubs return a
 * generic SUCCESS placeholder until Day 2.
 */
@Service
public class CreditControlService {

    private static final Logger log = LoggerFactory.getLogger(CreditControlService.class);

    private final LedgerService ledger;
    private final ReservationRepository reservations;
    private final CcSessionRepository sessions;
    private final String originHost;
    private final String originRealm;

    public CreditControlService(LedgerService ledger,
                                ReservationRepository reservations,
                                CcSessionRepository sessions,
                                @Value("${diameter.origin-host}") String originHost,
                                @Value("${diameter.origin-realm}") String originRealm) {
        this.ledger = ledger;
        this.reservations = reservations;
        this.sessions = sessions;
        this.originHost = originHost;
        this.originRealm = originRealm;
    }

    @Transactional
    public CreditControlAnswer handle(CreditControlRequest ccr) {
        ReservationKey key = new ReservationKey(ccr.sessionId(), ccr.ccRequestNumber());
        Optional<Reservation> cached = reservations.findById(key);
        if (cached.isPresent()) {
            log.info("Replay detected — returning cached answer for session={} req#={}",
                ccr.sessionId(), ccr.ccRequestNumber());
            return rebuildFromCache(ccr, cached.get());
        }

        return switch (ccr.ccRequestType()) {
            case INITIAL     -> handleInitial(ccr, key);
            case UPDATE      -> handleUpdate(ccr, key);
            case TERMINATION -> handleTerminate(ccr, key);
            case EVENT       -> handleEvent(ccr, key);
        };
    }

    private CreditControlAnswer handleInitial(CreditControlRequest ccr, ReservationKey key) {
        if (ccr.subscriptionId() == null
            || ccr.subscriptionId().type() != SubscriptionId.END_USER_E164
            || ccr.subscriptionId().data() == null) {
            return reject(ccr, ResultCode.DIAMETER_MISSING_AVP, key, 0L, 0L, null);
        }
        if (ccr.requestedUnits() == null || ccr.requestedUnits().ccTimeSeconds() == null) {
            return reject(ccr, ResultCode.DIAMETER_MISSING_AVP, key, 0L, 0L, null);
        }

        String msisdn = ccr.subscriptionId().data();
        long requested = ccr.requestedUnits().ccTimeSeconds();

        long granted = ledger.reserve(msisdn, requested, ccr.sessionId(), ccr.ccRequestNumber());
        if (granted == 0L) {
            log.info("CCR-Initial denied — msisdn={} requested={} balance insufficient", msisdn, requested);
            return persistAndReturn(ccr, ResultCode.DIAMETER_CREDIT_LIMIT_REACHED, key, requested, 0L, 0L);
        }

        sessions.findById(ccr.sessionId()).ifPresentOrElse(
            existing -> existing.recordGrant(granted, ccr.ccRequestNumber()),
            () -> {
                CcSession s = new CcSession(ccr.sessionId(), msisdn);
                s.recordGrant(granted, ccr.ccRequestNumber());
                sessions.save(s);
            }
        );

        log.info("CCR-Initial granted — msisdn={} requested={} granted={}", msisdn, requested, granted);
        return persistAndReturn(ccr, ResultCode.DIAMETER_SUCCESS, key, requested, 0L, granted);
    }

    /** Block 6 stub — full Update logic ships in Day 2. */
    private CreditControlAnswer handleUpdate(CreditControlRequest ccr, ReservationKey key) {
        log.warn("CCR-Update handling is a Day-2 stub — returning SUCCESS with no new grant");
        return persistAndReturn(ccr, ResultCode.DIAMETER_SUCCESS, key, 0L, 0L, 0L);
    }

    /** Block 6 stub — full Terminate logic ships in Day 2. */
    private CreditControlAnswer handleTerminate(CreditControlRequest ccr, ReservationKey key) {
        log.warn("CCR-Termination handling is a Day-2 stub — returning SUCCESS");
        return persistAndReturn(ccr, ResultCode.DIAMETER_SUCCESS, key, 0L, 0L, 0L);
    }

    private CreditControlAnswer handleEvent(CreditControlRequest ccr, ReservationKey key) {
        log.warn("CCR-Event not implemented — returning DIAMETER_AUTHORIZATION_REJECTED");
        return persistAndReturn(ccr, ResultCode.DIAMETER_AUTHORIZATION_REJECTED, key, 0L, 0L, 0L);
    }

    private CreditControlAnswer reject(CreditControlRequest ccr,
                                       ResultCode code,
                                       ReservationKey key,
                                       long requested,
                                       long granted,
                                       Long usedRequested) {
        log.warn("Rejecting CCR — session={} req#={} reason={}",
            ccr.sessionId(), ccr.ccRequestNumber(), code);
        return persistAndReturn(ccr, code, key, requested, 0L, granted);
    }

    private CreditControlAnswer persistAndReturn(CreditControlRequest ccr,
                                                 ResultCode resultCode,
                                                 ReservationKey key,
                                                 long requested,
                                                 long usedUnits,
                                                 long grantedUnits) {
        ServiceUnit grantedSu = grantedUnits > 0 ? ServiceUnit.time(grantedUnits) : null;
        CreditControlAnswer answer = new CreditControlAnswer(
            ccr.sessionId(),
            resultCode.value(),
            originHost,
            originRealm,
            ccr.authApplicationId(),
            ccr.ccRequestType(),
            ccr.ccRequestNumber(),
            grantedSu,
            null
        );

        Reservation row = new Reservation(
            ccr.sessionId(),
            ccr.ccRequestNumber(),
            (short) ccr.ccRequestType().code(),
            requested == 0L ? null : requested,
            usedUnits,
            grantedUnits,
            resultCode.value(),
            new byte[0]  // ADR-004: cca_avp_blob reserved for future byte-identical replay
        );
        reservations.save(row);
        return answer;
    }

    private CreditControlAnswer rebuildFromCache(CreditControlRequest ccr, Reservation cached) {
        ServiceUnit grantedSu = cached.getGrantedUnits() > 0 ? ServiceUnit.time(cached.getGrantedUnits()) : null;
        return new CreditControlAnswer(
            ccr.sessionId(),
            cached.getResultCode(),
            originHost,
            originRealm,
            ccr.authApplicationId(),
            CcRequestType.fromCode(cached.getCcRequestType()),
            cached.getCcRequestNumber(),
            grantedSu,
            null
        );
    }
}
