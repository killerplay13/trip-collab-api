-- =========================
-- Trip Collaboration Tool v0.1 Schema (PostgreSQL)
-- =========================

-- Extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================
-- trips
-- =========================
CREATE TABLE IF NOT EXISTS trips (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(100) NOT NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Taipei',
  notes TEXT NULL,

  invite_token_hash CHAR(64) NOT NULL UNIQUE,
  invite_enabled BOOLEAN NOT NULL DEFAULT TRUE,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trips_created_at ON trips(created_at);

-- =========================
-- trip_members
-- =========================
CREATE TABLE IF NOT EXISTS trip_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,

  nickname VARCHAR(50) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'member', -- owner / member

  member_token_hash CHAR(64) NOT NULL UNIQUE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,

  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ NULL,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_trip_members_role CHECK (role IN ('owner', 'member'))
);

CREATE INDEX IF NOT EXISTS idx_trip_members_trip_id ON trip_members(trip_id);

-- (Optional) Avoid duplicate nicknames in the same trip.
-- If you want to allow duplicate nicknames, comment this out.
CREATE UNIQUE INDEX IF NOT EXISTS ux_trip_members_trip_nickname ON trip_members(trip_id, nickname);

-- =========================
-- itinerary_items
-- =========================
CREATE TABLE IF NOT EXISTS itinerary_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,

  day_date DATE NOT NULL,
  start_time TIME NULL,
  end_time TIME NULL,

  title VARCHAR(120) NOT NULL,
  location_name VARCHAR(120) NULL,
  map_url TEXT NULL,
  note TEXT NULL,

  sort_order INT NOT NULL DEFAULT 0,

  created_by_member_id UUID NULL REFERENCES trip_members(id) ON DELETE SET NULL,
  updated_by_member_id UUID NULL REFERENCES trip_members(id) ON DELETE SET NULL,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_itinerary_trip_day ON itinerary_items(trip_id, day_date);
CREATE INDEX IF NOT EXISTS idx_itinerary_trip_day_sort ON itinerary_items(trip_id, day_date, sort_order);

-- =========================
-- expenses
-- =========================
CREATE TABLE IF NOT EXISTS expenses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,

  title VARCHAR(120) NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'TWD',

  paid_by_member_id UUID NOT NULL REFERENCES trip_members(id) ON DELETE RESTRICT,

  expense_date DATE NOT NULL DEFAULT CURRENT_DATE,
  note TEXT NULL,

  created_by_member_id UUID NULL REFERENCES trip_members(id) ON DELETE SET NULL,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_expenses_amount_nonnegative CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_expenses_trip_date ON expenses(trip_id, expense_date);
CREATE INDEX IF NOT EXISTS idx_expenses_trip_payer ON expenses(trip_id, paid_by_member_id);

-- =========================
-- expense_splits
-- =========================
CREATE TABLE IF NOT EXISTS expense_splits (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  expense_id UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
  member_id UUID NOT NULL REFERENCES trip_members(id) ON DELETE RESTRICT,

  share_amount NUMERIC(12,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_splits_amount_nonnegative CHECK (share_amount >= 0),
  CONSTRAINT ux_splits_expense_member UNIQUE (expense_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_splits_member ON expense_splits(member_id);

-- =========================
-- updated_at auto-update trigger helper
-- =========================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach triggers
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_trips_set_updated_at') THEN
    CREATE TRIGGER trg_trips_set_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_trip_members_set_updated_at') THEN
    CREATE TRIGGER trg_trip_members_set_updated_at
    BEFORE UPDATE ON trip_members
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_itinerary_items_set_updated_at') THEN
    CREATE TRIGGER trg_itinerary_items_set_updated_at
    BEFORE UPDATE ON itinerary_items
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_expenses_set_updated_at') THEN
    CREATE TRIGGER trg_expenses_set_updated_at
    BEFORE UPDATE ON expenses
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
  END IF;
END $$;
