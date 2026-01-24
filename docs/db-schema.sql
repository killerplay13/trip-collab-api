-- Trip Collaboration Tool (MVP) - v0.1 schema
-- Target: PostgreSQL
-- Notes:
-- - Uses pgcrypto for gen_random_uuid()
-- - Token values are stored as SHA-256 hex hashes (char(64)), not plaintext.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================
-- trips
-- =========================
CREATE TABLE IF NOT EXISTS trips (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title varchar(100) NOT NULL,
  start_date date NULL,
  end_date date NULL,
  timezone varchar(64) NOT NULL DEFAULT 'Asia/Taipei',
  notes text NULL,

  invite_token_hash char(64) NOT NULL UNIQUE,
  invite_enabled boolean NOT NULL DEFAULT true,

  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trips_created_at ON trips (created_at);

-- =========================
-- trip_members
-- =========================
CREATE TABLE IF NOT EXISTS trip_members (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id) ON DELETE CASCADE,

  nickname varchar(50) NOT NULL,
  role varchar(20) NOT NULL DEFAULT 'member', -- 'owner' | 'member'

  member_token_hash char(64) NOT NULL UNIQUE,

  is_active boolean NOT NULL DEFAULT true,
  joined_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NULL,

  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trip_members_trip_id ON trip_members (trip_id);

-- (Optional) prevent duplicate nickname within the same trip
-- If you prefer allowing same nickname, delete this constraint.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uq_trip_members_trip_nickname'
  ) THEN
    ALTER TABLE trip_members
      ADD CONSTRAINT uq_trip_members_trip_nickname UNIQUE (trip_id, nickname);
  END IF;
END$$;

-- =========================
-- itinerary_items
-- =========================
CREATE TABLE IF NOT EXISTS itinerary_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id) ON DELETE CASCADE,

  day_date date NOT NULL,
  start_time time NULL,
  end_time time NULL,

  title varchar(120) NOT NULL,
  location_name varchar(120) NULL,
  map_url text NULL,
  note text NULL,

  sort_order int NOT NULL DEFAULT 0,

  created_by_member_id uuid NULL REFERENCES trip_members(id) ON DELETE SET NULL,
  updated_by_member_id uuid NULL REFERENCES trip_members(id) ON DELETE SET NULL,

  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_itinerary_trip_day ON itinerary_items (trip_id, day_date);
CREATE INDEX IF NOT EXISTS idx_itinerary_trip_day_sort ON itinerary_items (trip_id, day_date, sort_order);

-- =========================
-- expenses
-- =========================
CREATE TABLE IF NOT EXISTS expenses (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id) ON DELETE CASCADE,

  title varchar(120) NOT NULL,
  amount numeric(12,2) NOT NULL CHECK (amount >= 0),
  currency char(3) NOT NULL DEFAULT 'TWD',

  paid_by_member_id uuid NOT NULL REFERENCES trip_members(id) ON DELETE RESTRICT,
  expense_date date NOT NULL DEFAULT current_date,

  note text NULL,
  created_by_member_id uuid NULL REFERENCES trip_members(id) ON DELETE SET NULL,

  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_expenses_trip_date ON expenses (trip_id, expense_date);
CREATE INDEX IF NOT EXISTS idx_expenses_trip_payer ON expenses (trip_id, paid_by_member_id);

-- =========================
-- expense_splits
-- =========================
CREATE TABLE IF NOT EXISTS expense_splits (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),

  expense_id uuid NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
  member_id uuid NOT NULL REFERENCES trip_members(id) ON DELETE RESTRICT,

  share_amount numeric(12,2) NOT NULL CHECK (share_amount >= 0),

  created_at timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT uq_expense_splits_expense_member UNIQUE (expense_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_expense_splits_member_id ON expense_splits (member_id);

COMMIT;
