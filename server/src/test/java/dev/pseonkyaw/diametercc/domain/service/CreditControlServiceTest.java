package dev.pseonkyaw.diametercc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class CreditControlServiceTest {

    private LedgerService ledger;
    private ReservationRepository reservations;
    private CcSessionRepository sessions;
    private CreditControlService service;

    @BeforeEach
    void setUp() {
        ledger = mock(LedgerService.class);
        reservations = mock(ReservationRepository.class);
        sessions = mock(CcSessionRepository.class);
        service = new CreditControlService(
            ledger, reservations, sessions, "diameter-cc.local", "pseonkyaw.dev");
    }

    private static CreditControlRequest ccrInitial(String sessionId, String msisdn, long requestedSeconds) {
        return new CreditControlRequest(
            sessionId,
            "test-client.local",
            "pseonkyaw.dev",
            "pseonkyaw.dev",
            4L,
            "32251@3gpp.org",
            CcRequestType.INITIAL,
            0,
            new SubscriptionId(SubscriptionId.END_USER_E164, msisdn),
            ServiceUnit.time(requestedSeconds),
            null,
            null
        );
    }

    @Test
    void successfulInitialReturnsGrantedQuotaAndPersistsReservation() {
        when(reservations.findById(any())).thenReturn(Optional.empty());
        when(ledger.reserve(eq("33745146129"), eq(60L), anyString(), anyInt())).thenReturn(60L);
        when(sessions.findById(anyString())).thenReturn(Optional.empty());

        CreditControlAnswer cca = service.handle(ccrInitial("sess-A", "33745146129", 60L));

        assertThat(cca.resultCode()).isEqualTo(ResultCode.DIAMETER_SUCCESS.value());
        assertThat(cca.grantedUnits()).isNotNull();
        assertThat(cca.grantedUnits().ccTimeSeconds()).isEqualTo(60L);
        assertThat(cca.originHost()).isEqualTo("diameter-cc.local");
        assertThat(cca.originRealm()).isEqualTo("pseonkyaw.dev");
        verify(reservations).save(any(Reservation.class));
        verify(sessions).save(any());
    }

    @Test
    void initialAgainstEmptyAccountReturns4012CreditLimitReached() {
        when(reservations.findById(any())).thenReturn(Optional.empty());
        when(ledger.reserve(anyString(), anyLong(), anyString(), anyInt())).thenReturn(0L);

        CreditControlAnswer cca = service.handle(ccrInitial("sess-B", "33611223344", 60L));

        assertThat(cca.resultCode()).isEqualTo(ResultCode.DIAMETER_CREDIT_LIMIT_REACHED.value());
        assertThat(cca.grantedUnits()).isNull();
        verify(reservations).save(any(Reservation.class));
        verify(sessions, times(0)).save(any());
    }

    @Test
    void replayReturnsCachedAnswerWithoutCallingLedger() {
        Reservation cached = new Reservation(
            "sess-C", 0, (short) 1, 60L, 0L, 60L,
            ResultCode.DIAMETER_SUCCESS.value(), new byte[0]);
        when(reservations.findById(any())).thenReturn(Optional.of(cached));

        CreditControlAnswer cca = service.handle(ccrInitial("sess-C", "33745146129", 60L));

        assertThat(cca.resultCode()).isEqualTo(ResultCode.DIAMETER_SUCCESS.value());
        assertThat(cca.grantedUnits().ccTimeSeconds()).isEqualTo(60L);
        verify(ledger, times(0)).reserve(anyString(), anyLong(), anyString(), anyInt());
        verify(reservations, times(0)).save(any());
    }

    private static CreditControlRequest ccrUpdate(String sessionId, int requestNumber,
                                                  long usedSeconds, long requestedSeconds) {
        return new CreditControlRequest(
            sessionId, "test-client.local", "pseonkyaw.dev", "pseonkyaw.dev",
            4L, "32251@3gpp.org", CcRequestType.UPDATE, requestNumber,
            null,
            requestedSeconds > 0 ? ServiceUnit.time(requestedSeconds) : null,
            ServiceUnit.time(usedSeconds),
            null
        );
    }

    private static CreditControlRequest ccrTerminate(String sessionId, int requestNumber, long usedSeconds) {
        return new CreditControlRequest(
            sessionId, "test-client.local", "pseonkyaw.dev", "pseonkyaw.dev",
            4L, "32251@3gpp.org", CcRequestType.TERMINATION, requestNumber,
            null,
            null,
            ServiceUnit.time(usedSeconds),
            1
        );
    }

    @Test
    void updateRefundsUnusedAndIssuesNewGrant() {
        when(reservations.findById(new ReservationKey("sess-U", 1))).thenReturn(Optional.empty());
        // Previous reservation row #0 had granted=60
        when(reservations.findById(new ReservationKey("sess-U", 0)))
            .thenReturn(Optional.of(new Reservation(
                "sess-U", 0, (short) 1, 60L, 0L, 60L,
                ResultCode.DIAMETER_SUCCESS.value(), new byte[0])));
        CcSession existing = new CcSession("sess-U", "33745146129");
        existing.recordGrant(60L, 0);
        when(sessions.findById("sess-U")).thenReturn(Optional.of(existing));
        when(ledger.reserve(eq("33745146129"), eq(30L), eq("sess-U"), eq(1))).thenReturn(30L);

        CreditControlAnswer cca = service.handle(ccrUpdate("sess-U", 1, 50L, 30L));

        assertThat(cca.resultCode()).isEqualTo(ResultCode.DIAMETER_SUCCESS.value());
        assertThat(cca.grantedUnits().ccTimeSeconds()).isEqualTo(30L);

        // Refund 10 unused (60 outstanding - 50 used)
        verify(ledger).refund(eq("33745146129"), eq(10L), eq("sess-U"), eq(1));
        // DEBIT for 50 actually used
        verify(ledger).recordDebit(eq("33745146129"), eq(50L), eq("sess-U"), eq(1));
        // RESERVE 30 new
        verify(ledger).reserve(eq("33745146129"), eq(30L), eq("sess-U"), eq(1));
        verify(reservations).save(any(Reservation.class));
    }

    @Test
    void updateOnUnknownSessionRejects() {
        when(reservations.findById(any())).thenReturn(Optional.empty());
        when(sessions.findById("sess-Z")).thenReturn(Optional.empty());

        CreditControlAnswer cca = service.handle(ccrUpdate("sess-Z", 1, 30L, 60L));

        assertThat(cca.resultCode()).isEqualTo(ResultCode.DIAMETER_AUTHORIZATION_REJECTED.value());
        verify(ledger, times(0)).refund(anyString(), anyLong(), anyString(), anyInt());
        verify(ledger, times(0)).reserve(anyString(), anyLong(), anyString(), anyInt());
    }

    @Test
    void terminateRefundsUnusedAndClosesSession() {
        when(reservations.findById(new ReservationKey("sess-T", 2))).thenReturn(Optional.empty());
        when(reservations.findById(new ReservationKey("sess-T", 1)))
            .thenReturn(Optional.of(new Reservation(
                "sess-T", 1, (short) 2, 30L, 0L, 30L,
                ResultCode.DIAMETER_SUCCESS.value(), new byte[0])));
        CcSession existing = new CcSession("sess-T", "33745146129");
        existing.recordGrant(30L, 1);
        when(sessions.findById("sess-T")).thenReturn(Optional.of(existing));

        CreditControlAnswer cca = service.handle(ccrTerminate("sess-T", 2, 25L));

        assertThat(cca.resultCode()).isEqualTo(ResultCode.DIAMETER_SUCCESS.value());
        assertThat(cca.grantedUnits()).isNull();
        verify(ledger).refund(eq("33745146129"), eq(5L), eq("sess-T"), eq(2));
        verify(ledger).recordDebit(eq("33745146129"), eq(25L), eq("sess-T"), eq(2));
        verify(ledger, times(0)).reserve(anyString(), anyLong(), anyString(), anyInt());
        assertThat(existing.getState()).isEqualTo(CcSession.State.TERMINATED);
    }

    @Test
    void terminateOfAlreadyTerminatedSessionReturnsSuccessIdempotently() {
        when(reservations.findById(any())).thenReturn(Optional.empty());
        CcSession terminated = new CcSession("sess-X", "33745146129");
        terminated.terminate(2);
        when(sessions.findById("sess-X")).thenReturn(Optional.of(terminated));

        CreditControlAnswer cca = service.handle(ccrTerminate("sess-X", 3, 10L));

        assertThat(cca.resultCode()).isEqualTo(ResultCode.DIAMETER_SUCCESS.value());
        verify(ledger, times(0)).refund(anyString(), anyLong(), anyString(), anyInt());
        verify(ledger, times(0)).recordDebit(anyString(), anyLong(), anyString(), anyInt());
    }

    @Test
    void initialMissingSubscriptionIdReturnsMissingAvp() {
        when(reservations.findById(any())).thenReturn(Optional.empty());

        CreditControlRequest ccr = new CreditControlRequest(
            "sess-D", "test-client.local", "pseonkyaw.dev", "pseonkyaw.dev",
            4L, "32251@3gpp.org", CcRequestType.INITIAL, 0,
            null,                          // no Subscription-Id
            ServiceUnit.time(60L),
            null, null
        );

        CreditControlAnswer cca = service.handle(ccr);
        assertThat(cca.resultCode()).isEqualTo(ResultCode.DIAMETER_MISSING_AVP.value());
        verify(ledger, times(0)).reserve(anyString(), anyLong(), anyString(), anyInt());
    }
}
