CREATE TABLE sanctuary_anchors (
    id TEXT PRIMARY KEY NOT NULL,
    sanctuary_id TEXT NOT NULL,
    parent_anchor_id TEXT NULL,
    type TEXT NOT NULL,
    world TEXT NULL,
    x INTEGER NULL,
    y INTEGER NULL,
    z INTEGER NULL,
    tier INTEGER NOT NULL CHECK (tier >= 1),
    anchor_generation INTEGER NOT NULL CHECK (anchor_generation >= 1),
    territory_radius REAL NOT NULL CHECK (territory_radius > 0),
    state TEXT NOT NULL,
    destroyed_at TEXT NULL,
    destruction_reason TEXT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (
        (world IS NULL AND x IS NULL AND y IS NULL AND z IS NULL)
        OR
        (world IS NOT NULL AND x IS NOT NULL AND y IS NOT NULL AND z IS NOT NULL)
    ),
    FOREIGN KEY (sanctuary_id) REFERENCES sanctuaries(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_anchor_id) REFERENCES sanctuary_anchors(id) ON DELETE RESTRICT
);

CREATE INDEX idx_sanctuary_anchors_sanctuary
    ON sanctuary_anchors(sanctuary_id);

CREATE INDEX idx_sanctuary_anchors_world_state
    ON sanctuary_anchors(world, state);

CREATE UNIQUE INDEX idx_sanctuary_anchors_position
    ON sanctuary_anchors(world, x, y, z)
    WHERE world IS NOT NULL;

INSERT INTO sanctuary_anchors (
    id,
    sanctuary_id,
    parent_anchor_id,
    type,
    world,
    x,
    y,
    z,
    tier,
    anchor_generation,
    territory_radius,
    state,
    destroyed_at,
    destruction_reason,
    created_at,
    updated_at
)
SELECT
    id,
    id,
    NULL,
    type,
    world,
    x,
    y,
    z,
    tier,
    anchor_generation,
    territory_radius,
    state,
    destroyed_at,
    destruction_reason,
    created_at,
    updated_at
FROM sanctuaries;

CREATE TABLE sanctuary_anchor_edges (
    anchor_a_id TEXT NOT NULL,
    anchor_b_id TEXT NOT NULL,
    PRIMARY KEY (anchor_a_id, anchor_b_id),
    CHECK (anchor_a_id < anchor_b_id),
    FOREIGN KEY (anchor_a_id) REFERENCES sanctuary_anchors(id) ON DELETE CASCADE,
    FOREIGN KEY (anchor_b_id) REFERENCES sanctuary_anchors(id) ON DELETE CASCADE
);

CREATE INDEX idx_sanctuary_anchor_edges_a
    ON sanctuary_anchor_edges(anchor_a_id);

CREATE INDEX idx_sanctuary_anchor_edges_b
    ON sanctuary_anchor_edges(anchor_b_id);

CREATE TABLE anchor_effect_levels (
    anchor_id TEXT NOT NULL,
    effect TEXT NOT NULL,
    level INTEGER NOT NULL CHECK (level >= 1),
    PRIMARY KEY (anchor_id, effect),
    FOREIGN KEY (anchor_id) REFERENCES sanctuary_anchors(id) ON DELETE CASCADE
);

INSERT OR REPLACE INTO anchor_effect_levels(anchor_id, effect, level)
SELECT sanctuary_id, effect, level
FROM sanctuary_effect_levels;
