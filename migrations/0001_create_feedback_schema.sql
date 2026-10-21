CREATE TABLE IF NOT EXISTS feedback_submissions (
  id TEXT NOT NULL PRIMARY KEY,
  client_submission_id TEXT NOT NULL UNIQUE
    CHECK (length(client_submission_id) BETWEEN 16 AND 100),
  category TEXT NOT NULL CHECK (category IN ('problem', 'improvement', 'general')),
  category_value TEXT NOT NULL CHECK (
    (category = 'problem' AND category_value IN (
      'calculation-ocr',
      'match-results-standings',
      'app-crash',
      'login-cloud-sync',
      'export-download',
      'other'
    )) OR
    (category = 'improvement' AND category_value IN (
      'calculation-ocr',
      'match-results-standings',
      'templates-export',
      'app-design-usability',
      'other'
    )) OR
    (category = 'general' AND category_value IN ('excellent', 'good', 'average', 'poor'))
  ),
  description TEXT NOT NULL CHECK (length(description) BETWEEN 1 AND 4000),
  email TEXT CHECK (email IS NULL OR length(email) <= 254),
  attachment_key TEXT CHECK (
    attachment_key IS NULL OR (
      category = 'problem' AND
      length(attachment_key) BETWEEN 1 AND 256 AND
      attachment_key LIKE 'feedback/%'
    )
  ),
  status TEXT NOT NULL DEFAULT 'new' CHECK (status IN ('new', 'reviewing', 'resolved', 'dismissed')),
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS feedback_submissions_created_at_idx
  ON feedback_submissions (created_at);

CREATE INDEX IF NOT EXISTS feedback_submissions_status_idx
  ON feedback_submissions (status);

CREATE TABLE IF NOT EXISTS feedback_rate_limits (
  bucket_key TEXT NOT NULL PRIMARY KEY,
  window_start INTEGER NOT NULL,
  request_count INTEGER NOT NULL CHECK (request_count >= 0)
);

CREATE INDEX IF NOT EXISTS feedback_rate_limits_window_start_idx
  ON feedback_rate_limits (window_start);
