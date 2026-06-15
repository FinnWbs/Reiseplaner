CREATE TABLE activity_external_refs (
  id BIGSERIAL PRIMARY KEY,
  activity_id BIGINT NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
  source VARCHAR(32) NOT NULL,
  external_id VARCHAR(255) NOT NULL,
  CONSTRAINT uq_activity_external_ref UNIQUE (source, external_id)
);

CREATE INDEX idx_activity_external_refs_activity ON activity_external_refs(activity_id);
