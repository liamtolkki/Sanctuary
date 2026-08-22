ALTER TABLE sanctuaries
    ADD COLUMN territory_radius REAL NOT NULL DEFAULT 1.0 CHECK (territory_radius > 0);
