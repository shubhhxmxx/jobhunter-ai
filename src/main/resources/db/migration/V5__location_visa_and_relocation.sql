-- Other Indian cities and visa-sponsored jobs abroad are on by default; where each job sits relative to the candidate is stored with the match.
ALTER TABLE candidate_preferences ADD COLUMN open_other_indian_cities BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE candidate_preferences ADD COLUMN open_abroad_with_visa BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE job_match ADD COLUMN work_mode VARCHAR(30);
ALTER TABLE job_match ADD COLUMN work_label VARCHAR(300);
ALTER TABLE job_match ADD COLUMN relocation BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_match ADD COLUMN visa VARCHAR(10);
