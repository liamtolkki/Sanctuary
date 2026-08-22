CREATE TABLE sanctuary_security (
    sanctuary_id TEXT PRIMARY KEY NOT NULL,
    security_mode TEXT NOT NULL DEFAULT 'NORMAL' CHECK (security_mode IN ('NORMAL', 'LOCKDOWN')),
    FOREIGN KEY (sanctuary_id) REFERENCES sanctuaries(id) ON DELETE CASCADE
);

CREATE TABLE sanctuary_blacklist (
    sanctuary_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (sanctuary_id, player_uuid),
    FOREIGN KEY (sanctuary_id) REFERENCES sanctuaries(id) ON DELETE CASCADE
);

CREATE INDEX idx_sanctuary_blacklist_player
    ON sanctuary_blacklist(player_uuid);
