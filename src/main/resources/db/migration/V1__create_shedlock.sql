-- ShedLock: ensures scheduled jobs (outbox poller, reconciliation) run on one instance at a time
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
