-- =====================================================================
-- V4 · search_logs is an append-only log, like audit_events: writing a log must never fail because the
-- referenced customer changed or disappeared (it would leave a "poison" event in the outbox forever).
-- The foreign key is dropped; an index keeps per-customer queries fast.
-- (V1 is never edited: applied migrations are immutable.)
-- =====================================================================

ALTER TABLE search_logs DROP CONSTRAINT fk_search_logs_customer;

CREATE INDEX ix_search_logs_customer_id ON search_logs (customer_id);
