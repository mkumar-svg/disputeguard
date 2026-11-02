-- v2: demo-store order details, dispute <-> order link, and AI evidence-pack fields.

ALTER TABLE orders ADD COLUMN description      TEXT;
ALTER TABLE orders ADD COLUMN status           VARCHAR(32);
ALTER TABLE orders ADD COLUMN scenario         VARCHAR(32);
ALTER TABLE orders ADD COLUMN shipping_address TEXT;
ALTER TABLE orders ADD COLUMN captured_at      TIMESTAMPTZ;

ALTER TABLE disputes ADD COLUMN order_id             BIGINT REFERENCES orders (id);
ALTER TABLE disputes ADD COLUMN buyer_transaction_id VARCHAR(64);
ALTER TABLE disputes ADD COLUMN gross_amount         NUMERIC(12, 2);
ALTER TABLE disputes ADD COLUMN dispute_created_at   TIMESTAMPTZ;
ALTER TABLE disputes ADD COLUMN buyer_message        TEXT;

ALTER TABLE evidence_packs ADD COLUMN status            VARCHAR(32);
ALTER TABLE evidence_packs ADD COLUMN reason_summary    TEXT;
ALTER TABLE evidence_packs ADD COLUMN base_probability  NUMERIC(4, 3);
ALTER TABLE evidence_packs ADD COLUMN adjustment_reason TEXT;
ALTER TABLE evidence_packs ADD COLUMN suggested_refund  NUMERIC(12, 2);
ALTER TABLE evidence_packs ADD COLUMN guardrail_notes   TEXT;
ALTER TABLE evidence_packs ADD COLUMN agent_trace       TEXT;
ALTER TABLE evidence_packs ADD COLUMN model             VARCHAR(128);
ALTER TABLE evidence_packs ADD COLUMN submit_action     VARCHAR(32);
ALTER TABLE evidence_packs ADD COLUMN submit_response   TEXT;
ALTER TABLE evidence_packs ADD COLUMN updated_at        TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_disputes_order ON disputes (order_id);
