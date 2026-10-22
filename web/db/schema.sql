-- Run with a backend-only PostgreSQL role. Browser clients use Supabase Auth only.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE IF NOT EXISTS orders (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, tier text NOT NULL CHECK (tier IN ('FAMILY_PRO','CLINICAL_SLP')),
  currency text NOT NULL CHECK (currency IN ('EGP','SAR','USD')), interval text NOT NULL CHECK (interval IN ('monthly','annual')),
  amount integer NOT NULL CHECK (amount > 0), gateway text NOT NULL, status text NOT NULL,
  gateway_id text, created_at bigint NOT NULL, billing_email text
);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS billing_email text;
-- Keep historical USD orders; new checkout requests accept only EGP and SAR.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_currency_check;
ALTER TABLE orders ADD CONSTRAINT orders_currency_check CHECK (currency IN ('EGP','SAR','USD'));
CREATE TABLE IF NOT EXISTS subscriptions (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, order_id uuid NOT NULL UNIQUE REFERENCES orders(id), gateway text NOT NULL,
  gateway_subscription_id text UNIQUE, gateway_customer_id text, tier text NOT NULL, status text NOT NULL,
  current_period_end bigint NOT NULL, updated_at bigint NOT NULL
);
CREATE TABLE IF NOT EXISTS licenses (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, subscription_id uuid NOT NULL UNIQUE REFERENCES subscriptions(id),
  license_key text NOT NULL UNIQUE, tier text NOT NULL, max_devices integer NOT NULL CHECK (max_devices BETWEEN 1 AND 100),
  is_revoked boolean NOT NULL DEFAULT false, created_at bigint NOT NULL
);
CREATE TABLE IF NOT EXISTS device_activations (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, license_id uuid NOT NULL REFERENCES licenses(id), device_id uuid NOT NULL,
  activated_at bigint NOT NULL, last_ping_at bigint NOT NULL, UNIQUE (license_id,device_id)
);
CREATE TABLE IF NOT EXISTS activation_exchanges (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, license_id uuid NOT NULL REFERENCES licenses(id),
  code_hash text NOT NULL UNIQUE, expires_at bigint NOT NULL, device_id uuid, redeemed_at bigint
);
CREATE TABLE IF NOT EXISTS webhook_events (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, gateway text NOT NULL, gateway_event_id text NOT NULL,
  processed_at bigint NOT NULL, UNIQUE (gateway,gateway_event_id)
);
CREATE INDEX IF NOT EXISTS orders_owner ON orders(user_id);
CREATE INDEX IF NOT EXISTS licenses_owner ON licenses(user_id);
CREATE INDEX IF NOT EXISTS exchanges_expiry ON activation_exchanges(expires_at);
-- No table contains audio, biometric templates, child names, or card details.
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE licenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_activations ENABLE ROW LEVEL SECURITY;
ALTER TABLE activation_exchanges ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_events ENABLE ROW LEVEL SECURITY;
CREATE TABLE IF NOT EXISTS reports (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, subject_id uuid NOT NULL, report_id uuid NOT NULL,
  generated_at bigint NOT NULL, summary jsonb NOT NULL, UNIQUE(user_id,report_id)
);
CREATE INDEX IF NOT EXISTS reports_owner_recent ON reports(user_id,generated_at DESC);
ALTER TABLE reports ENABLE ROW LEVEL SECURITY;
-- No public policies: all access flows through authenticated, ownership-checked server routes.
