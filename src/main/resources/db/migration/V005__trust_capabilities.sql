CREATE TABLE IF NOT EXISTS sanctuary_trust (
    sanctuary_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (sanctuary_id, player_uuid),
    FOREIGN KEY (sanctuary_id) REFERENCES sanctuaries(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sanctuary_capabilities (
    sanctuary_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    capability TEXT NOT NULL CHECK (
        capability IN ('BUILD', 'BREAK', 'INTERACT', 'CONTAINER', 'REDSTONE', 'ENTITIES')
    ),
    PRIMARY KEY (sanctuary_id, player_uuid, capability),
    FOREIGN KEY (sanctuary_id, player_uuid)
        REFERENCES sanctuary_trust(sanctuary_id, player_uuid)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sanctuary_trust_player
    ON sanctuary_trust(player_uuid);
