ALTER TABLE trips
  ADD COLUMN start_date DATE,
  ADD COLUMN end_date DATE,
  ADD COLUMN pace VARCHAR(32) NOT NULL DEFAULT 'BALANCED',
  ADD COLUMN day_rhythm VARCHAR(32) NOT NULL DEFAULT 'BALANCED',
  ADD COLUMN destination_source VARCHAR(32) NOT NULL DEFAULT 'KNOWN';

ALTER TABLE trip_days
  ADD COLUMN travel_date DATE,
  ADD COLUMN available_from INTEGER NOT NULL DEFAULT 540,
  ADD COLUMN available_until INTEGER NOT NULL DEFAULT 1200;

ALTER TABLE trip_day_activities
  ADD COLUMN scheduled_start INTEGER NOT NULL DEFAULT 540,
  ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 90;

ALTER TABLE trip_days
  ADD CONSTRAINT chk_trip_day_availability
  CHECK (available_from >= 0 AND available_from < available_until AND available_until <= 1440);

ALTER TABLE trip_day_activities
  ADD CONSTRAINT chk_trip_activity_schedule
  CHECK (
    scheduled_start >= 0
    AND scheduled_start <= 1440
    AND duration_minutes > 0
    AND duration_minutes <= 720
  );

CREATE INDEX idx_trip_days_travel_date ON trip_days(travel_date);
