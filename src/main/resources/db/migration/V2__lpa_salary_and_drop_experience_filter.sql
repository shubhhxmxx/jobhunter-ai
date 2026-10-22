-- salary_floor is now in LPA (lakh INR per year); earlier values used another scale, so clear them.
UPDATE candidate_preferences SET salary_floor = NULL;
ALTER TABLE candidate_preferences DROP COLUMN min_years;
ALTER TABLE candidate_preferences DROP COLUMN max_years;
