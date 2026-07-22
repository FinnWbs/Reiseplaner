ALTER TABLE activities
  ALTER COLUMN external_id TYPE TEXT,
  ALTER COLUMN name TYPE TEXT;

ALTER TABLE activity_external_refs
  ALTER COLUMN external_id TYPE TEXT;
