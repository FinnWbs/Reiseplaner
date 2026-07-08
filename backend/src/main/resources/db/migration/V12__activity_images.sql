CREATE TABLE activity_images (
  id BIGSERIAL PRIMARY KEY,
  activity_id BIGINT NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
  source VARCHAR(40) NOT NULL,
  provider_ref TEXT NOT NULL,
  url TEXT,
  alt TEXT NOT NULL,
  credit TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_activity_image_provider_ref UNIQUE (activity_id, source, provider_ref)
);

CREATE INDEX idx_activity_images_activity ON activity_images(activity_id);
