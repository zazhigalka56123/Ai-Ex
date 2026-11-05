CREATE TABLE IF NOT EXISTS notifications (
    id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL,
    type varchar(48) NOT NULL,
    payload text NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    sent_at timestamptz
);
CREATE INDEX IF NOT EXISTS ix_notifications_recipient_created ON notifications (recipient_id, created_at DESC, id DESC);
