ALTER TABLE activities
  ADD COLUMN canonical_category VARCHAR(40),
  ADD COLUMN notability_score DOUBLE PRECISION NOT NULL DEFAULT 0,
  ADD COLUMN quality_score DOUBLE PRECISION NOT NULL DEFAULT 0,
  ADD COLUMN category_fit_score DOUBLE PRECISION NOT NULL DEFAULT 0,
  ADD COLUMN itinerary_fit_score DOUBLE PRECISION NOT NULL DEFAULT 0,
  ADD COLUMN final_score DOUBLE PRECISION NOT NULL DEFAULT 0,
  ADD COLUMN quality_reason_codes TEXT;

ALTER TABLE activities
  ADD COLUMN IF NOT EXISTS popularity_score DOUBLE PRECISION NOT NULL DEFAULT 0;

CREATE INDEX idx_activities_final_score
  ON activities(city, active, primary_interest, final_score DESC);
