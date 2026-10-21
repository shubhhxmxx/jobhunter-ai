CREATE TABLE candidate_profile (
  id UUID PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  email VARCHAR(320) NOT NULL UNIQUE,
  api_key_hash VARCHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE resume (
  id UUID PRIMARY KEY,
  candidate_id UUID NOT NULL REFERENCES candidate_profile(id),
  version INTEGER NOT NULL,
  filename VARCHAR(300) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 VARCHAR(64) NOT NULL,
  text_content TEXT NOT NULL,
  parsed_json TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  UNIQUE (candidate_id, version)
);
CREATE TABLE candidate_preferences (
  candidate_id UUID PRIMARY KEY REFERENCES candidate_profile(id),
  target_titles TEXT NOT NULL,
  locations TEXT NOT NULL,
  remote_ok BOOLEAN NOT NULL,
  salary_floor INTEGER,
  years_experience INTEGER,
  min_years INTEGER,
  max_years INTEGER,
  min_match_score INTEGER NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE provider_credentials (
  id UUID PRIMARY KEY,
  candidate_id UUID NOT NULL REFERENCES candidate_profile(id),
  provider VARCHAR(50) NOT NULL,
  model VARCHAR(100) NOT NULL,
  encrypted_key TEXT NOT NULL,
  key_hint VARCHAR(10) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  UNIQUE (candidate_id, provider)
);
CREATE TABLE company (
  id UUID PRIMARY KEY,
  name VARCHAR(300) NOT NULL,
  name_key VARCHAR(300) NOT NULL UNIQUE
);
CREATE TABLE job (
  id UUID PRIMARY KEY,
  source VARCHAR(50) NOT NULL,
  external_id VARCHAR(300) NOT NULL,
  company_id UUID NOT NULL REFERENCES company(id),
  title VARCHAR(500) NOT NULL,
  location VARCHAR(300),
  remote BOOLEAN NOT NULL,
  salary_min INTEGER,
  salary_max INTEGER,
  description TEXT NOT NULL,
  source_url VARCHAR(2000) NOT NULL,
  dedup_key VARCHAR(900) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL,
  retrieved_at TIMESTAMP WITH TIME ZONE NOT NULL,
  UNIQUE (source, external_id)
);
CREATE TABLE search_run (
  id UUID PRIMARY KEY,
  candidate_id UUID NOT NULL REFERENCES candidate_profile(id),
  status VARCHAR(30) NOT NULL,
  min_score INTEGER NOT NULL,
  jobs_found INTEGER NOT NULL DEFAULT 0,
  jobs_matched INTEGER NOT NULL DEFAULT 0,
  error VARCHAR(500),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  started_at TIMESTAMP WITH TIME ZONE,
  finished_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE source_run (
  id UUID PRIMARY KEY,
  search_run_id UUID NOT NULL REFERENCES search_run(id),
  source VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL,
  fetched INTEGER NOT NULL DEFAULT 0,
  error VARCHAR(500),
  started_at TIMESTAMP WITH TIME ZONE NOT NULL,
  finished_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE job_match (
  id UUID PRIMARY KEY,
  candidate_id UUID NOT NULL REFERENCES candidate_profile(id),
  job_id UUID NOT NULL REFERENCES job(id),
  resume_id UUID NOT NULL REFERENCES resume(id),
  resume_version INTEGER NOT NULL,
  prefs_hash VARCHAR(64) NOT NULL,
  scoring_version VARCHAR(20) NOT NULL,
  eligible BOOLEAN NOT NULL,
  score INTEGER NOT NULL,
  rejection_reasons TEXT NOT NULL,
  matched_skills TEXT NOT NULL,
  missing_requirements TEXT NOT NULL,
  evidence TEXT NOT NULL,
  uncertainty TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  UNIQUE (candidate_id, job_id, resume_id, prefs_hash, scoring_version)
);
CREATE INDEX idx_job_match_rank ON job_match (candidate_id, resume_id, prefs_hash, eligible, score);

-- Tables for later milestones (contact discovery, outreach). Not used by the API yet.
CREATE TABLE person (
  id UUID PRIMARY KEY,
  company_id UUID REFERENCES company(id),
  full_name VARCHAR(300) NOT NULL,
  title VARCHAR(300),
  role_type VARCHAR(40) NOT NULL,
  profile_url VARCHAR(2000),
  UNIQUE (company_id, profile_url)
);
CREATE TABLE job_person (
  id UUID PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES job(id),
  person_id UUID NOT NULL REFERENCES person(id),
  evidence TEXT NOT NULL,
  provenance VARCHAR(500) NOT NULL,
  confidence INTEGER NOT NULL,
  UNIQUE (job_id, person_id)
);
CREATE TABLE contact_channel (
  id UUID PRIMARY KEY,
  person_id UUID NOT NULL REFERENCES person(id),
  kind VARCHAR(30) NOT NULL,
  channel_value VARCHAR(500) NOT NULL,
  verified BOOLEAN NOT NULL,
  provenance VARCHAR(500) NOT NULL,
  UNIQUE (person_id, kind, channel_value)
);
CREATE TABLE outreach (
  id UUID PRIMARY KEY,
  candidate_id UUID NOT NULL REFERENCES candidate_profile(id),
  job_id UUID NOT NULL REFERENCES job(id),
  person_id UUID REFERENCES person(id),
  subject VARCHAR(300),
  body TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  approved_at TIMESTAMP WITH TIME ZONE
);
