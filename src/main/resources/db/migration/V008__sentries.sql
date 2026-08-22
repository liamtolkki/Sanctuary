CREATE TABLE sanctuary_sentry_defaults (
    sanctuary_id TEXT NOT NULL,
    trigger_name TEXT NOT NULL,
    enabled INTEGER NOT NULL,
    PRIMARY KEY (sanctuary_id, trigger_name),
    FOREIGN KEY (sanctuary_id) REFERENCES sanctuaries(id) ON DELETE CASCADE
);

CREATE TABLE sentries (
    id TEXT PRIMARY KEY NOT NULL,
    sanctuary_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    world TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    entity_uuid TEXT NULL,
    state TEXT NOT NULL,
    respawn_at TEXT NULL,
    recall_deadline TEXT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(world, x, y, z),
    FOREIGN KEY (sanctuary_id) REFERENCES sanctuaries(id) ON DELETE CASCADE
);

CREATE INDEX idx_sentries_sanctuary ON sentries(sanctuary_id);
CREATE UNIQUE INDEX idx_sentries_entity ON sentries(entity_uuid) WHERE entity_uuid IS NOT NULL;

CREATE TABLE sentry_overrides (
    sentry_id TEXT NOT NULL,
    trigger_name TEXT NOT NULL,
    override_value TEXT NOT NULL,
    PRIMARY KEY (sentry_id, trigger_name),
    FOREIGN KEY (sentry_id) REFERENCES sentries(id) ON DELETE CASCADE
);
