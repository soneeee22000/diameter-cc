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

import dev.pseonkyaw.diametercc.domain.model.Reservation;
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
