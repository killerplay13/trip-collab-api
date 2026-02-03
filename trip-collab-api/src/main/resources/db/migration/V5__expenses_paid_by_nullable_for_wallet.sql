ALTER TABLE expenses

  ALTER COLUMN paid_by_member_id DROP NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_expenses_paid_by_required_for_personal'
  ) THEN
    ALTER TABLE expenses
      ADD CONSTRAINT chk_expenses_paid_by_required_for_personal
      CHECK (
        (payment_source = 'PERSONAL' AND paid_by_member_id IS NOT NULL)
        OR
        (payment_source = 'SHARED_WALLET' AND paid_by_member_id IS NULL)
      );
  END IF;
END $$;
