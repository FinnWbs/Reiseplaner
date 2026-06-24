ALTER TABLE activities
  ADD COLUMN primary_interest VARCHAR(40),
  ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN import_version INTEGER NOT NULL DEFAULT 1;

UPDATE activities
SET active = FALSE
WHERE source = 'GEOAPIFY';

CREATE INDEX idx_activities_active_interest
  ON activities(city, active, primary_interest);

CREATE TABLE activity_import_states (
  id BIGSERIAL PRIMARY KEY,
  city VARCHAR(120) NOT NULL,
  interest_code VARCHAR(40) NOT NULL,
  import_version INTEGER NOT NULL,
  synced_at TIMESTAMP NOT NULL,
  CONSTRAINT uq_activity_import_state UNIQUE (city, interest_code)
);

CREATE TABLE trip_interests (
  trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
  interest_id BIGINT NOT NULL REFERENCES interests(id) ON DELETE CASCADE,
  PRIMARY KEY (trip_id, interest_id)
);
