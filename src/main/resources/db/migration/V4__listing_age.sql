-- When the source says a job was posted, and how many earlier monthly threads already listed it.
ALTER TABLE job ADD COLUMN posted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE job ADD COLUMN repost_count INTEGER NOT NULL DEFAULT 0;
