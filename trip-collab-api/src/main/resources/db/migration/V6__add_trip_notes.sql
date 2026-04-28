-- =========================
-- trip_notes
-- =========================
CREATE TABLE IF NOT EXISTS trip_notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
  author_id UUID NOT NULL REFERENCES trip_members(id) ON DELETE CASCADE,

  title VARCHAR(255) NOT NULL,
  content TEXT,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trip_notes_trip_id ON trip_notes(trip_id);

-- Attach trigger for updated_at
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_trip_notes_set_updated_at') THEN
    CREATE TRIGGER trg_trip_notes_set_updated_at
    BEFORE UPDATE ON trip_notes
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
  END IF;
END $$;
