CREATE TABLE feedback_entries (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  page_url TEXT,
  target_label TEXT,
  target_selector TEXT,
  screenshot_data_url TEXT,
  description TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_feedback_entries_created_at ON feedback_entries(created_at DESC);
