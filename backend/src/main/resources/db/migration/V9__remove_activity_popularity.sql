DELETE FROM activity_external_refs
WHERE source = 'GOOGLE_PLACES';

DROP INDEX IF EXISTS idx_activities_popularity;

ALTER TABLE activities
  DROP COLUMN IF EXISTS rating_count,
  DROP COLUMN IF EXISTS popularity_score,
  DROP COLUMN IF EXISTS popularity_status,
  DROP COLUMN IF EXISTS popularity_source,
  DROP COLUMN IF EXISTS popularity_fetched_at;
