CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_active_order
    ON payment (order_id)
    WHERE status IN ('PENDING', 'SUCCEEDED', 'PARTIALLY_REFUNDED') AND late=false;
ALTER table callback_outbox ALTER COLUMN payload TYPE text;