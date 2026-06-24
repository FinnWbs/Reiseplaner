ALTER TABLE activities
  ADD COLUMN rating_count INTEGER CHECK (rating_count >= 0),
  ADD COLUMN popularity_score DOUBLE PRECISION CHECK (popularity_score >= 0 AND popularity_score <= 100),
  ADD COLUMN popularity_status VARCHAR(32) NOT NULL DEFAULT 'QUALITY_FALLBACK',
  ADD COLUMN popularity_source VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_QUALITY',
  ADD COLUMN popularity_fetched_at TIMESTAMP;

UPDATE activities
SET popularity_score = LEAST(100, GREATEST(0, data_quality_score * 100))
WHERE popularity_score IS NULL;

CREATE INDEX idx_activities_popularity
  ON activities(city, active, primary_interest, popularity_status, popularity_score DESC);
