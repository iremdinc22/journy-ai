-- Nullable: historical trips retain their existing locality lookup and rows are not rewritten.
ALTER TABLE trips ADD COLUMN IF NOT EXISTS destination_query VARCHAR(255);
