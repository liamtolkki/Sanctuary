ALTER TABLE sanctuaries
    ADD COLUMN debug_ephemeral INTEGER NOT NULL DEFAULT 0 CHECK (debug_ephemeral IN (0, 1));
