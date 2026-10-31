-- DisputeGuard schema v1: demo store data + disputes + AI evidence packs.

CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    paypal_order_id  VARCHAR(64)  UNIQUE,
    capture_id       VARCHAR(64)  UNIQUE,
    amount           NUMERIC(12, 2) NOT NULL,
    currency         VARCHAR(3)   NOT NULL,
    items            TEXT,
    buyer_email      VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE shipments (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT       NOT NULL REFERENCES orders (id),
    carrier       VARCHAR(64),
    tracking_no   VARCHAR(128),
    events        TEXT,
    delivered_at  TIMESTAMPTZ
);

CREATE TABLE messages (
    id        BIGSERIAL PRIMARY KEY,
    order_id  BIGINT       NOT NULL REFERENCES orders (id),
    sender    VARCHAR(16)  NOT NULL,
    text      TEXT         NOT NULL,
    sent_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE disputes (
    id                   BIGSERIAL PRIMARY KEY,
    paypal_dispute_id    VARCHAR(64)  NOT NULL UNIQUE,
    reason               VARCHAR(64),
    status               VARCHAR(64),
    amount               NUMERIC(12, 2),
    currency             VARCHAR(3),
    seller_response_due  TIMESTAMPTZ,
    transaction_id       VARCHAR(64),
    raw_json             TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE evidence_packs (
    id               BIGSERIAL PRIMARY KEY,
    dispute_id       BIGINT       NOT NULL REFERENCES disputes (id),
    recommendation   VARCHAR(32),
    win_probability  NUMERIC(4, 3),
    checklist        TEXT,
    draft_note       TEXT,
    files            TEXT,
    approved_by      VARCHAR(255),
    submitted_at     TIMESTAMPTZ,
    outcome          VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_shipments_order ON shipments (order_id);
CREATE INDEX idx_messages_order ON messages (order_id);
CREATE INDEX idx_evidence_dispute ON evidence_packs (dispute_id);
