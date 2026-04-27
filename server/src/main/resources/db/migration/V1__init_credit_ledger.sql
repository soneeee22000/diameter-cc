-- diameter-cc — initial credit ledger schema (RFC 4006 Gy interface).
-- See docs/ARCHITECTURE-diameter.md §5 for design rationale.
CREATE TABLE credit_account (
  msisdn VARCHAR(20) PRIMARY KEY,
  balance_units BIGINT NOT NULL CHECK (balance_units >= 0),
  unit_type VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now (),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now ()
);

CREATE TABLE cc_session (
  session_id VARCHAR(255) PRIMARY KEY,
  msisdn VARCHAR(20) NOT NULL REFERENCES credit_account (msisdn),
  state VARCHAR(16) NOT NULL,
  granted_units_total BIGINT NOT NULL DEFAULT 0,
  used_units_total BIGINT NOT NULL DEFAULT 0,
  last_request_number INTEGER NOT NULL DEFAULT -1,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now (),
  ended_at TIMESTAMPTZ
);

CREATE INDEX idx_cc_session_msisdn ON cc_session (msisdn);

CREATE INDEX idx_cc_session_state ON cc_session (state);

CREATE TABLE reservation (
  session_id VARCHAR(255) NOT NULL,
  cc_request_number INTEGER NOT NULL,
  cc_request_type SMALLINT NOT NULL,
  requested_units BIGINT,
  used_units BIGINT NOT NULL DEFAULT 0,
  granted_units BIGINT NOT NULL DEFAULT 0,
  result_code INTEGER NOT NULL,
  cca_avp_blob BYTEA NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now (),
  PRIMARY KEY (session_id, cc_request_number)
);

CREATE TABLE ledger_transaction (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(255) NOT NULL,
  msisdn VARCHAR(20) NOT NULL,
  op VARCHAR(8) NOT NULL,
  units BIGINT NOT NULL,
  balance_after BIGINT NOT NULL,
  cc_request_number INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now ()
);

CREATE INDEX idx_ledger_msisdn_at ON ledger_transaction (msisdn, created_at DESC);

CREATE INDEX idx_ledger_session ON ledger_transaction (session_id);
