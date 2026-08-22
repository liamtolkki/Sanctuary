ALTER TABLE sanctuaries
    ADD COLUMN anchor_generation INTEGER NOT NULL DEFAULT 1 CHECK (anchor_generation >= 1);

ALTER TABLE sanctuaries
    ADD COLUMN destroyed_at TEXT NULL;

ALTER TABLE sanctuaries
    ADD COLUMN destruction_reason TEXT NULL;
