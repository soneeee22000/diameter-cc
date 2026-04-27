package dev.pseonkyaw.diametercc.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.pseonkyaw.diametercc.domain.model.Reservation;
import dev.pseonkyaw.diametercc.domain.model.ReservationKey;

public interface ReservationRepository extends JpaRepository<Reservation, ReservationKey> {
}
