-- V3__expenses_add_fx_fields.sql

ALTER TABLE expenses ADD COLUMN IF NOT EXISTS original_amount NUMERIC(12,2) NULL;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS original_currency CHAR(3) NULL;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS fx_rate NUMERIC(18,8) NULL;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS fx_source VARCHAR(20) NULL;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS amount_overridden BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_expenses_fx_fields_consistency'
  ) THEN
    ALTER TABLE expenses
    ADD CONSTRAINT ck_expenses_fx_fields_consistency
    CHECK (
      (original_currency IS NULL AND original_amount IS NULL AND fx_rate IS NULL)
      OR
      (original_currency IS NOT NULL AND original_amount IS NOT NULL AND fx_rate IS NOT NULL)
    );
  END IF;
END $$;
