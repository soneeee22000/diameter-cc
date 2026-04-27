-- Demo prepaid accounts for the bundled test client and integration tests.
-- See docs/ARCHITECTURE-diameter.md §5.
INSERT INTO
  credit_account (msisdn, balance_units, unit_type)
VALUES
  ('33745146129', 600, 'CC_TIME'),
  ('33611223344', 0, 'CC_TIME'),
  ('33799887766', 1800, 'CC_TIME');
