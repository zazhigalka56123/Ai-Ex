CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL,
    type varchar(48) NOT NULL,
    payload text NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    sent_at timestamptz
);
CREATE INDEX ix_notifications_recipient_created ON notifications (recipient_id, created_at DESC, id DESC);
INSERT INTO notifications (id, recipient_id, type, payload, status, attempts, created_at, sent_at)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
        'PERSONA_READY', '{"versionNo":2}', 'SENT', 1, now(), now());
