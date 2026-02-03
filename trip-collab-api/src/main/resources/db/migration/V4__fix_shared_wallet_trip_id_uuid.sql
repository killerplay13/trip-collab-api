-- V4__fix_shared_wallet_trip_id_uuid.sql
-- Shared Wallet (FX-enabled) for Trip-Collab
-- Postgres + Flyway

-- NOTE:
-- 1) Wallet base currency always equals trips.currency (enforced by service; DB stores base_currency for convenience)
-- 2) Wallet supports multi-currency holdings via wallet_balances.
-- 3) All wallet transactions store original currency/amount + fx_rate + computed_base_amount for reporting.
-- 4) Exchange is represented by two transactions (OUT + IN) sharing the same exchange_group_id.

BEGIN;

-- 1) Shared wallet (1 trip : 1 wallet)
CREATE TABLE IF NOT EXISTS shared_wallets (
  id              BIGSERIAL PRIMARY KEY,
  trip_id         UUID NOT NULL UNIQUE,
  base_currency   CHAR(3) NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT fk_shared_wallets_trip
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_shared_wallets_trip_id
  ON shared_wallets (trip_id);

-- 2) Wallet balances (holdings per currency)
CREATE TABLE IF NOT EXISTS wallet_balances (
  id          BIGSERIAL PRIMARY KEY,
  wallet_id   BIGINT NOT NULL,
  currency    CHAR(3) NOT NULL,
  balance     NUMERIC(18,6) NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT fk_wallet_balances_wallet
    FOREIGN KEY (wallet_id) REFERENCES shared_wallets(id) ON DELETE CASCADE,

  CONSTRAINT uq_wallet_balances_wallet_currency
    UNIQUE (wallet_id, currency)
);

CREATE INDEX IF NOT EXISTS idx_wallet_balances_wallet_id
  ON wallet_balances (wallet_id);

CREATE INDEX IF NOT EXISTS idx_wallet_balances_currency
  ON wallet_balances (currency);

-- 3) Wallet transactions (ledger)
CREATE TABLE IF NOT EXISTS wallet_transactions (
  id                   BIGSERIAL PRIMARY KEY,
  wallet_id             BIGINT NOT NULL,

  -- type: DEPOSIT | EXPENSE | EXCHANGE | WITHDRAW | ADJUSTMENT
  txn_type              VARCHAR(20) NOT NULL,
  -- direction: IN | OUT (amount is always positive)
  direction             VARCHAR(10) NOT NULL,

  -- amount in original currency (always positive)
  original_amount       NUMERIC(18,6) NOT NULL,
  original_currency     CHAR(3) NOT NULL,

  -- fx_rate converts original -> base_currency (1 if original_currency == base_currency)
  fx_rate               NUMERIC(18,10) NOT NULL DEFAULT 1,

  -- computed in wallet base currency for reporting/reconciliation
  computed_base_amount  NUMERIC(18,6) NOT NULL,

  -- optional linkages
  member_id             UUID NULL,
  expense_id            UUID NULL,

  -- exchange grouping: one exchange produces two transactions (OUT + IN) with same group id
  exchange_group_id     UUID NULL,

  fx_source             VARCHAR(50) NULL,
  note                  TEXT NULL,

  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT fk_wallet_transactions_wallet
    FOREIGN KEY (wallet_id) REFERENCES shared_wallets(id) ON DELETE CASCADE,

  -- If you already have trip_members table and want linkage:
  CONSTRAINT fk_wallet_transactions_member
    FOREIGN KEY (member_id) REFERENCES trip_members(id) ON DELETE SET NULL,

  -- If you already have expenses table:
  CONSTRAINT fk_wallet_transactions_expense
    FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE SET NULL,

  -- validations
  CONSTRAINT chk_wallet_transactions_txn_type
    CHECK (txn_type IN ('DEPOSIT','EXPENSE','EXCHANGE','WITHDRAW','ADJUSTMENT')),

  CONSTRAINT chk_wallet_transactions_direction
    CHECK (direction IN ('IN','OUT')),

  CONSTRAINT chk_wallet_transactions_amount_positive
    CHECK (original_amount > 0),

  CONSTRAINT chk_wallet_transactions_fx_rate_positive
    CHECK (fx_rate > 0),

  CONSTRAINT chk_wallet_transactions_computed_base_positive
    CHECK (computed_base_amount >= 0),

  -- EXPENSE txn must reference expense_id; others should not be required
  CONSTRAINT chk_wallet_transactions_expense_link
    CHECK (
      (txn_type = 'EXPENSE' AND expense_id IS NOT NULL)
      OR
      (txn_type <> 'EXPENSE')
    )
);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_id_created_at
  ON wallet_transactions (wallet_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_exchange_group_id
  ON wallet_transactions (exchange_group_id);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_expense_id
  ON wallet_transactions (expense_id);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_member_id
  ON wallet_transactions (member_id);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_type
  ON wallet_transactions (txn_type);

-- 4) Add payment_source to expenses
-- payment_source: PERSONAL | SHARED_WALLET
ALTER TABLE expenses
  ADD COLUMN IF NOT EXISTS payment_source VARCHAR(20) NOT NULL DEFAULT 'PERSONAL';

ALTER TABLE expenses
  ADD CONSTRAINT chk_expenses_payment_source
  CHECK (payment_source IN ('PERSONAL','SHARED_WALLET'));

CREATE INDEX IF NOT EXISTS idx_expenses_payment_source
  ON expenses (payment_source);

COMMIT;
