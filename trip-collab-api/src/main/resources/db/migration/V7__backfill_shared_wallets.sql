INSERT INTO shared_wallets (trip_id, base_currency, created_at, updated_at)
SELECT t.id, COALESCE(NULLIF(TRIM(t.currency), ''), 'TWD'), NOW(), NOW()
FROM trips t
WHERE NOT EXISTS (
  SELECT 1
  FROM shared_wallets sw
  WHERE sw.trip_id = t.id
);
