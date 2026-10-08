CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    recipient_id UUID NOT NULL,
    type VARCHAR(48) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_notification_type CHECK (type IN (
        'IMPORT_PARSED', 'IMPORT_FAILED', 'PERSONA_READY', 'PERSONA_ARCHIVED',
        'CONSULTATION_REQUESTED', 'CONSULTATION_STATUS_CHANGED', 'FLAG_RESOLVED'
    )),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_notification_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_notification_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_notifications_recipient_created ON notifications (recipient_id, created_at DESC, id DESC);
