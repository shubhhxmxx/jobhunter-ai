-- Sign-in: a passcode per profile (stored as a salted PBKDF2 hash) and one access key per signed-in device.
ALTER TABLE candidate_profile ADD COLUMN passcode_hash VARCHAR(200);
CREATE TABLE login_session (
  id UUID PRIMARY KEY,
  candidate_id UUID NOT NULL REFERENCES candidate_profile(id),
  key_hash VARCHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
-- Optional: also show jobs abroad that do not mention a visa (flagged and ranked lower).
ALTER TABLE candidate_preferences ADD COLUMN abroad_without_visa BOOLEAN NOT NULL DEFAULT FALSE;
