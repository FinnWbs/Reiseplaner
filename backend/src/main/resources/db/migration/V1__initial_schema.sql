CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE interests (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE user_interests (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  interest_id BIGINT NOT NULL REFERENCES interests(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, interest_id)
);

CREATE TABLE activities (
  id BIGSERIAL PRIMARY KEY,
  external_id VARCHAR(255) NOT NULL,
  source VARCHAR(32) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  city VARCHAR(120) NOT NULL,
  category VARCHAR(120),
  subcategory VARCHAR(120),
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  address VARCHAR(500),
  rating DOUBLE PRECISION,
  data_quality_score DOUBLE PRECISION NOT NULL DEFAULT 0,
  last_synced_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_activity_source_external UNIQUE (source, external_id)
);

CREATE INDEX idx_activities_city ON activities(city);
CREATE INDEX idx_activities_source_external ON activities(source, external_id);

CREATE TABLE activity_interests (
  id BIGSERIAL PRIMARY KEY,
  activity_id BIGINT NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
  interest_id BIGINT NOT NULL REFERENCES interests(id) ON DELETE CASCADE,
  score INTEGER NOT NULL CHECK (score >= 0 AND score <= 10),
  CONSTRAINT uq_activity_interest UNIQUE (activity_id, interest_id)
);

CREATE INDEX idx_activity_interests_interest_score ON activity_interests(interest_id, score);

CREATE TABLE trips (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  city VARCHAR(120) NOT NULL,
  days_count INTEGER NOT NULL CHECK (days_count > 0),
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trips_user ON trips(user_id);

CREATE TABLE trip_days (
  id BIGSERIAL PRIMARY KEY,
  trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
  day_number INTEGER NOT NULL CHECK (day_number > 0),
  CONSTRAINT uq_trip_day UNIQUE (trip_id, day_number)
);

CREATE TABLE trip_day_activities (
  id BIGSERIAL PRIMARY KEY,
  trip_day_id BIGINT NOT NULL REFERENCES trip_days(id) ON DELETE CASCADE,
  activity_id BIGINT NOT NULL REFERENCES activities(id),
  position INTEGER NOT NULL CHECK (position > 0),
  locked BOOLEAN NOT NULL DEFAULT FALSE,
  notes TEXT,
  CONSTRAINT uq_trip_day_activity_position UNIQUE (trip_day_id, position)
);
