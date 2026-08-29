CREATE TABLE sanctuary_aggression (
    sanctuary_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    hostile_until TEXT NOT NULL,
    PRIMARY KEY (sanctuary_id, player_uuid),
    FOREIGN KEY (sanctuary_id) REFERENCES sanctuaries(id) ON DELETE CASCADE
);

CREATE INDEX idx_sanctuary_aggression_player
    ON sanctuary_aggression(player_uuid);
