CREATE TABLE IF NOT EXISTS sanctuaries (
    id TEXT PRIMARY KEY NOT NULL,
    owner_uuid TEXT NOT NULL,
    type TEXT NOT NULL,
    name TEXT NOT NULL,
    world TEXT NULL,
    x INTEGER NULL,
    y INTEGER NULL,
    z INTEGER NULL,
    tier INTEGER NOT NULL,
    territory_area REAL NOT NULL,
    state TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (tier >= 1),
    CHECK (territory_area > 0),
    CHECK (
        (world IS NULL AND x IS NULL AND y IS NULL AND z IS NULL)
        OR
        (world IS NOT NULL AND x IS NOT NULL AND y IS NOT NULL AND z IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_sanctuaries_owner_uuid
    ON sanctuaries(owner_uuid);

CREATE INDEX IF NOT EXISTS idx_sanctuaries_world_state
    ON sanctuaries(world, state);
