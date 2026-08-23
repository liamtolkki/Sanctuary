CREATE TABLE altar_offering_progress (
    player_uuid TEXT PRIMARY KEY NOT NULL,
    completed_offerings INTEGER NOT NULL DEFAULT 0 CHECK (completed_offerings BETWEEN 0 AND 12),
    divine_relic_awarded INTEGER NOT NULL DEFAULT 0 CHECK (divine_relic_awarded IN (0, 1))
);
