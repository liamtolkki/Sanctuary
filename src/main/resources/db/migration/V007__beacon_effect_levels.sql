CREATE TABLE sanctuary_effect_levels (
    sanctuary_id TEXT NOT NULL,
    effect TEXT NOT NULL,
    level INTEGER NOT NULL CHECK (level >= 1),
    PRIMARY KEY (sanctuary_id, effect),
    FOREIGN KEY (sanctuary_id) REFERENCES sanctuaries(id) ON DELETE CASCADE
);
