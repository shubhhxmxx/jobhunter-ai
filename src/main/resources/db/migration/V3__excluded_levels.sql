-- Seniority levels to skip. Existing and new candidates start with staff and principal excluded.
ALTER TABLE candidate_preferences ADD COLUMN excluded_levels TEXT NOT NULL DEFAULT '["staff","principal"]';
