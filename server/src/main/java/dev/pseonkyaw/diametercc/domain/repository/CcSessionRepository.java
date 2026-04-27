package dev.pseonkyaw.diametercc.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.pseonkyaw.diametercc.domain.model.CcSession;

public interface CcSessionRepository extends JpaRepository<CcSession, String> {
}
